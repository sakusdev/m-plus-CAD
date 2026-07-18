# SPDX-License-Identifier: MPL-2.0
"""m+CAD Minecraft-to-Blender Live Link add-on."""

from __future__ import annotations

import json
import queue
from typing import Any

import bpy
from bpy.props import IntProperty, PointerProperty, StringProperty
from bpy.types import Operator, Panel, PropertyGroup

from .client import LiveLinkClient
from .scene_apply import apply_message, clear_scene

bl_info = {
    "name": "m+CAD Live Link",
    "author": "sakusdev / m+CAD contributors",
    "version": (0, 1, 0),
    "blender": (4, 0, 0),
    "location": "3D View > Sidebar > m+CAD",
    "description": "Live differential scene synchronization from the m+CAD Minecraft Mod",
    "category": "Import-Export",
}

_MESSAGES: queue.Queue[str] = queue.Queue()
_CLIENT: LiveLinkClient | None = None
_STATUS = "切断"
_TIMER_REGISTERED = False


class MCADLiveLinkSettings(PropertyGroup):
    host: StringProperty(name="Host", default="127.0.0.1")
    port: IntProperty(name="Port", default=8765, min=1, max=65535)
    token: StringProperty(name="Session Token", default="", subtype="PASSWORD")


class MCAD_OT_connect(Operator):
    bl_idname = "mcad.live_link_connect"
    bl_label = "接続"
    bl_description = "Minecraftのm+CAD Live Linkへ接続します"

    def execute(self, context: bpy.types.Context) -> set[str]:
        global _CLIENT, _STATUS
        settings = context.scene.mcad_live_link
        if not settings.token.strip():
            self.report({"ERROR"}, "Minecraftのm+CAD画面に表示されたtokenを入力してください")
            return {"CANCELLED"}
        if _CLIENT is not None:
            _CLIENT.stop()
        try:
            _CLIENT = LiveLinkClient(
                settings.host.strip(),
                int(settings.port),
                settings.token.strip(),
                _enqueue_message,
                _set_status,
            )
            _CLIENT.start()
            _STATUS = "接続開始"
            return {"FINISHED"}
        except (OSError, ValueError, RuntimeError) as error:
            _CLIENT = None
            self.report({"ERROR"}, str(error))
            return {"CANCELLED"}


class MCAD_OT_disconnect(Operator):
    bl_idname = "mcad.live_link_disconnect"
    bl_label = "切断"

    def execute(self, context: bpy.types.Context) -> set[str]:
        del context
        _disconnect()
        return {"FINISHED"}


class MCAD_OT_clear(Operator):
    bl_idname = "mcad.live_link_clear"
    bl_label = "Live Sceneを消去"
    bl_description = "m+CAD Live Linkが所有するCollectionとDataBlockだけを削除します"

    def execute(self, context: bpy.types.Context) -> set[str]:
        del context
        clear_scene()
        _set_status("Live Sceneを消去しました")
        return {"FINISHED"}


class MCAD_PT_live_link(Panel):
    bl_label = "m+CAD Live Link"
    bl_idname = "MCAD_PT_live_link"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "m+CAD"

    def draw(self, context: bpy.types.Context) -> None:
        layout = self.layout
        settings = context.scene.mcad_live_link
        layout.prop(settings, "host")
        layout.prop(settings, "port")
        layout.prop(settings, "token")
        row = layout.row(align=True)
        row.operator("mcad.live_link_connect", icon="LINKED")
        row.operator("mcad.live_link_disconnect", icon="UNLINKED")
        layout.operator("mcad.live_link_clear", icon="TRASH")
        layout.separator()
        layout.label(text=_STATUS, icon="INFO")
        layout.label(text="Minecraft: L=開始/停止, I=即時同期")


_CLASSES = (
    MCADLiveLinkSettings,
    MCAD_OT_connect,
    MCAD_OT_disconnect,
    MCAD_OT_clear,
    MCAD_PT_live_link,
)


def register() -> None:
    global _TIMER_REGISTERED
    for cls in _CLASSES:
        bpy.utils.register_class(cls)
    bpy.types.Scene.mcad_live_link = PointerProperty(type=MCADLiveLinkSettings)
    if not _TIMER_REGISTERED:
        bpy.app.timers.register(_pump_messages, first_interval=0.1, persistent=True)
        _TIMER_REGISTERED = True


def unregister() -> None:
    global _TIMER_REGISTERED
    _disconnect()
    if hasattr(bpy.types.Scene, "mcad_live_link"):
        del bpy.types.Scene.mcad_live_link
    for cls in reversed(_CLASSES):
        bpy.utils.unregister_class(cls)
    _TIMER_REGISTERED = False


def _enqueue_message(message: str) -> None:
    _MESSAGES.put(message)


def _set_status(status: str) -> None:
    global _STATUS
    _STATUS = status[:300]


def _pump_messages() -> float:
    global _STATUS
    processed = 0
    while processed < 8:
        try:
            raw = _MESSAGES.get_nowait()
        except queue.Empty:
            break
        try:
            value: Any = json.loads(raw)
            if not isinstance(value, dict):
                raise ValueError("Live Link message root must be an object")
            _STATUS = apply_message(value)
        except (ValueError, TypeError, KeyError, RuntimeError) as error:
            _STATUS = f"適用失敗: {error}"[:300]
        processed += 1
    return 0.1


def _disconnect() -> None:
    global _CLIENT, _STATUS
    current = _CLIENT
    _CLIENT = None
    if current is not None:
        current.stop()
    _STATUS = "切断"


if __name__ == "__main__":
    register()
