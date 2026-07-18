# SPDX-License-Identifier: MPL-2.0
"""Apply m+CAD Live Link scene-update messages on Blender's main thread."""

from __future__ import annotations

from typing import Any

import bpy

_COLLECTION_NAME = "m+CAD Live"
_ROOT_NAME = "m+CAD Live Root"
_TAG = "mcad_live_link"
_ID = "mcad_stable_id"
_KIND = "mcad_kind"


def apply_message(message: dict[str, Any]) -> str:
    """Apply one validated protocol message and return a human-readable result."""
    if message.get("protocol") != "mcad-live-link" or message.get("version") != 1:
        raise ValueError("unsupported m+CAD Live Link protocol")
    message_type = message.get("type")
    if message_type == "scene-clear":
        clear_scene()
        return f"Scene cleared: {message.get('reason', 'unknown')}"
    if message_type != "scene-update":
        raise ValueError(f"unsupported message type: {message_type}")

    if message.get("full"):
        clear_scene()
    collection, root = _ensure_root()
    scene = message.get("scene") or {}
    _apply_transform(root, scene.get("origin") or _identity_transform())
    root["mcad_scene_id"] = str(message.get("sceneId", ""))
    root["mcad_revision"] = int(message.get("revision", 0))
    root["mcad_roots"] = list(scene.get("roots") or [])

    removals = message.get("remove") or {}
    _remove_objects("node", removals.get("nodes") or [])
    _remove_objects("light", removals.get("lights") or [])
    _remove_objects("camera", removals.get("cameras") or [])
    _remove_objects("curve", removals.get("curves") or [])
    _remove_objects("bone", removals.get("bones") or [])
    _remove_objects("collision", removals.get("collisions") or [])
    _remove_meshes(removals.get("meshes") or [])
    _remove_materials(removals.get("materials") or [])

    upserts = message.get("upsert") or {}
    for value in upserts.get("materials") or []:
        _upsert_material(value)
    for value in upserts.get("meshes") or []:
        _upsert_mesh(value)

    node_values = list(upserts.get("nodes") or [])
    for value in node_values:
        _ensure_node(value, collection)
    for value in node_values:
        _configure_node(value, root, collection)

    for value in upserts.get("lights") or []:
        _upsert_light(value, root, collection)
    for value in upserts.get("cameras") or []:
        _upsert_camera(value, root, collection)
    for value in upserts.get("curves") or []:
        _upsert_curve(value, root, collection)
    for value in upserts.get("bones") or []:
        _upsert_bone(value, root, collection)
    for value in upserts.get("collisions") or []:
        _upsert_collision(value, root, collection)

    bpy.context.view_layer.update()
    upsert_count = sum(len(values or []) for values in upserts.values())
    remove_count = sum(len(values or []) for values in removals.values())
    return f"revision {message.get('revision', 0)}: upsert {upsert_count}, remove {remove_count}"


def clear_scene() -> None:
    """Remove only data blocks owned by m+CAD Live Link."""
    for obj in list(bpy.data.objects):
        if obj.get(_TAG):
            bpy.data.objects.remove(obj, do_unlink=True)
    for mesh in list(bpy.data.meshes):
        if mesh.get(_TAG):
            bpy.data.meshes.remove(mesh)
    for curve in list(bpy.data.curves):
        if curve.get(_TAG):
            bpy.data.curves.remove(curve)
    for light in list(bpy.data.lights):
        if light.get(_TAG):
            bpy.data.lights.remove(light)
    for camera in list(bpy.data.cameras):
        if camera.get(_TAG):
            bpy.data.cameras.remove(camera)
    for material in list(bpy.data.materials):
        if material.get(_TAG):
            bpy.data.materials.remove(material)
    collection = bpy.data.collections.get(_COLLECTION_NAME)
    if collection is not None:
        bpy.data.collections.remove(collection)


def _ensure_collection() -> bpy.types.Collection:
    collection = bpy.data.collections.get(_COLLECTION_NAME)
    if collection is None:
        collection = bpy.data.collections.new(_COLLECTION_NAME)
    if collection.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(collection)
    return collection


def _ensure_root() -> tuple[bpy.types.Collection, bpy.types.Object]:
    collection = _ensure_collection()
    root = _find_object("scene-root", "scene-root")
    if root is None:
        root = bpy.data.objects.new(_ROOT_NAME, None)
        root.empty_display_type = "CUBE"
        root.empty_display_size = 0.35
        _tag(root, "scene-root", "scene-root")
        collection.objects.link(root)
    elif root.name not in collection.objects:
        collection.objects.link(root)
    return collection, root


def _upsert_material(value: dict[str, Any]) -> bpy.types.Material:
    stable_id = str(value["id"])
    material = _find_material(stable_id)
    if material is None:
        material = bpy.data.materials.new(str(value.get("name") or stable_id))
        _tag(material, stable_id, "material")
    material.name = str(value.get("name") or stable_id)
    material.use_nodes = True
    nodes = material.node_tree.nodes if material.node_tree else None
    principled = nodes.get("Principled BSDF") if nodes else None
    base = _color4(value.get("baseColor"), (0.8, 0.8, 0.8, 1.0))
    if principled is not None:
        _set_input(principled, "Base Color", base)
        _set_input(principled, "Metallic", float(value.get("metallic", 0.0)))
        _set_input(principled, "Roughness", float(value.get("roughness", 0.5)))
        _set_input(principled, "Alpha", base[3])
        emission = _color3(value.get("emissiveColor"), (0.0, 0.0, 0.0))
        if "Emission Color" in principled.inputs:
            _set_input(principled, "Emission Color", (*emission, 1.0))
        elif "Emission" in principled.inputs:
            _set_input(principled, "Emission", (*emission, 1.0))
        _set_input(principled, "Emission Strength", float(value.get("emissiveStrength", 0.0)))
    alpha_mode = str(value.get("alphaMode", "opaque"))
    if hasattr(material, "surface_render_method"):
        material.surface_render_method = "DITHERED" if alpha_mode == "blend" else "DITHERED"
    elif hasattr(material, "blend_method"):
        material.blend_method = "BLEND" if alpha_mode == "blend" else "CLIP" if alpha_mode == "mask" else "OPAQUE"
    material.diffuse_color = base
    material["mcad_alpha_mode"] = alpha_mode
    if value.get("alphaCutoff") is not None:
        material["mcad_alpha_cutoff"] = float(value["alphaCutoff"])
    return material


def _upsert_mesh(value: dict[str, Any]) -> bpy.types.Mesh:
    stable_id = str(value["id"])
    mesh = _find_mesh(stable_id)
    if mesh is None:
        mesh = bpy.data.meshes.new(str(value.get("name") or stable_id))
        _tag(mesh, stable_id, "mesh")
    mesh.name = str(value.get("name") or stable_id)

    vertices: list[tuple[float, float, float]] = []
    faces: list[tuple[int, int, int]] = []
    face_material_ids: list[str | None] = []
    colors: list[tuple[float, float, float, float]] = []
    material_ids: list[str] = []
    for primitive in value.get("primitives") or []:
        offset = len(vertices)
        positions = [_vector3(item) for item in primitive.get("positions") or []]
        vertices.extend(positions)
        indices = [int(item) for item in primitive.get("indices") or []]
        primitive_faces = [
            (offset + indices[index], offset + indices[index + 1], offset + indices[index + 2])
            for index in range(0, len(indices), 3)
        ]
        faces.extend(primitive_faces)
        material_id = primitive.get("materialId")
        face_material_ids.extend([material_id] * len(primitive_faces))
        if material_id and material_id not in material_ids:
            material_ids.append(str(material_id))
        primitive_colors = primitive.get("colors") or []
        if primitive_colors:
            colors.extend([_color4(item, (1.0, 1.0, 1.0, 1.0)) for item in primitive_colors])
        else:
            colors.extend([(1.0, 1.0, 1.0, 1.0)] * len(positions))

    mesh.clear_geometry()
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.clear()
    material_index: dict[str, int] = {}
    for stable_material_id in material_ids:
        material = _find_material(stable_material_id)
        if material is not None:
            material_index[stable_material_id] = len(mesh.materials)
            mesh.materials.append(material)
    for polygon, stable_material_id in zip(mesh.polygons, face_material_ids, strict=False):
        if stable_material_id is not None:
            polygon.material_index = material_index.get(str(stable_material_id), 0)

    existing = mesh.color_attributes.get("m+CAD Color")
    if existing is not None:
        mesh.color_attributes.remove(existing)
    if colors and len(colors) == len(vertices):
        attribute = mesh.color_attributes.new(name="m+CAD Color", type="FLOAT_COLOR", domain="POINT")
        for item, color in zip(attribute.data, colors, strict=True):
            item.color = color
    mesh.update()
    return mesh


def _ensure_node(value: dict[str, Any], collection: bpy.types.Collection) -> bpy.types.Object:
    stable_id = str(value["id"])
    obj = _find_object(stable_id, "node")
    if obj is None:
        obj = bpy.data.objects.new(str(value.get("name") or stable_id), None)
        obj.empty_display_type = "PLAIN_AXES"
        obj.empty_display_size = 0.25
        _tag(obj, stable_id, "node")
        collection.objects.link(obj)
    obj.name = str(value.get("name") or stable_id)
    return obj


def _configure_node(
    value: dict[str, Any],
    root: bpy.types.Object,
    collection: bpy.types.Collection,
) -> None:
    stable_id = str(value["id"])
    obj = _ensure_node(value, collection)
    parent_id = value.get("parentId")
    obj.parent = _find_object(str(parent_id), "node") if parent_id else root
    if obj.parent is None:
        obj.parent = root
    _apply_transform(obj, value.get("transform") or _identity_transform())

    wanted: set[str] = set()
    for mesh_id_value in value.get("meshIds") or []:
        mesh_id = str(mesh_id_value)
        mesh = _find_mesh(mesh_id)
        if mesh is None:
            continue
        instance_id = f"{stable_id}::{mesh_id}"
        wanted.add(instance_id)
        instance = _find_object(instance_id, "mesh-instance")
        if instance is None:
            instance = bpy.data.objects.new(mesh.name, mesh)
            _tag(instance, instance_id, "mesh-instance")
            instance["mcad_node_id"] = stable_id
            instance["mcad_mesh_id"] = mesh_id
            collection.objects.link(instance)
        else:
            instance.data = mesh
        instance.parent = obj
        instance.location = (0.0, 0.0, 0.0)
        instance.rotation_mode = "QUATERNION"
        instance.rotation_quaternion = (1.0, 0.0, 0.0, 0.0)
        instance.scale = (1.0, 1.0, 1.0)
    for candidate in list(bpy.data.objects):
        if candidate.get(_TAG) and candidate.get(_KIND) == "mesh-instance":
            if candidate.get("mcad_node_id") == stable_id and candidate.get(_ID) not in wanted:
                bpy.data.objects.remove(candidate, do_unlink=True)


def _upsert_light(value: dict[str, Any], root: bpy.types.Object, collection: bpy.types.Collection) -> None:
    stable_id = str(value["id"])
    obj = _find_object(stable_id, "light")
    light_type = {"point": "POINT", "directional": "SUN", "spot": "SPOT", "area": "AREA"}.get(
        str(value.get("lightType", "point")), "POINT"
    )
    if obj is None or not isinstance(obj.data, bpy.types.Light) or obj.data.type != light_type:
        if obj is not None:
            bpy.data.objects.remove(obj, do_unlink=True)
        data = bpy.data.lights.new(str(value.get("name") or stable_id), type=light_type)
        _tag(data, stable_id, "light-data")
        obj = bpy.data.objects.new(str(value.get("name") or stable_id), data)
        _tag(obj, stable_id, "light")
        collection.objects.link(obj)
    obj.name = str(value.get("name") or stable_id)
    obj.parent = root
    _apply_transform(obj, value.get("transform") or _identity_transform())
    obj.data.color = _color3(value.get("color"), (1.0, 1.0, 1.0))
    obj.data.energy = float(value.get("intensity", 1.0))
    if value.get("range") is not None and hasattr(obj.data, "cutoff_distance"):
        obj.data.cutoff_distance = float(value["range"])
    if light_type == "SPOT" and value.get("outerCone") is not None:
        outer = float(value["outerCone"])
        inner = float(value.get("innerCone") or 0.0)
        obj.data.spot_size = max(outer * 2.0, 0.001)
        obj.data.spot_blend = max(0.0, min(1.0, 1.0 - inner / max(outer, 0.000001)))


def _upsert_camera(value: dict[str, Any], root: bpy.types.Object, collection: bpy.types.Collection) -> None:
    stable_id = str(value["id"])
    obj = _find_object(stable_id, "camera")
    if obj is None or not isinstance(obj.data, bpy.types.Camera):
        if obj is not None:
            bpy.data.objects.remove(obj, do_unlink=True)
        data = bpy.data.cameras.new(str(value.get("name") or stable_id))
        _tag(data, stable_id, "camera-data")
        obj = bpy.data.objects.new(str(value.get("name") or stable_id), data)
        _tag(obj, stable_id, "camera")
        collection.objects.link(obj)
    obj.name = str(value.get("name") or stable_id)
    obj.parent = root
    _apply_transform(obj, value.get("transform") or _identity_transform())
    obj.data.clip_start = float(value.get("nearPlane", 0.1))
    if value.get("farPlane") is not None:
        obj.data.clip_end = float(value["farPlane"])
    if value.get("projection") == "orthographic":
        obj.data.type = "ORTHO"
        obj.data.ortho_scale = float(value.get("orthographicHeight") or 1.0)
    else:
        obj.data.type = "PERSP"
        obj.data.angle = float(value.get("verticalFov") or 0.7853981633974483)


def _upsert_curve(value: dict[str, Any], root: bpy.types.Object, collection: bpy.types.Collection) -> None:
    stable_id = str(value["id"])
    obj = _find_object(stable_id, "curve")
    if obj is None or not isinstance(obj.data, bpy.types.Curve):
        if obj is not None:
            bpy.data.objects.remove(obj, do_unlink=True)
        data = bpy.data.curves.new(str(value.get("name") or stable_id), type="CURVE")
        data.dimensions = "3D"
        _tag(data, stable_id, "curve-data")
        obj = bpy.data.objects.new(str(value.get("name") or stable_id), data)
        _tag(obj, stable_id, "curve")
        collection.objects.link(obj)
    data = obj.data
    data.splines.clear()
    points = [_vector3(point) for point in value.get("points") or []]
    if points:
        spline = data.splines.new("POLY")
        spline.points.add(len(points) - 1)
        for target, point in zip(spline.points, points, strict=True):
            target.co = (*point, 1.0)
        spline.use_cyclic_u = bool(value.get("closed"))
    obj.name = str(value.get("name") or stable_id)
    obj.parent = root


def _upsert_bone(value: dict[str, Any], root: bpy.types.Object, collection: bpy.types.Collection) -> None:
    stable_id = str(value["id"])
    obj = _find_object(stable_id, "bone")
    if obj is None:
        obj = bpy.data.objects.new(str(value.get("name") or stable_id), None)
        obj.empty_display_type = "OCTAHEDRAL"
        obj.empty_display_size = 0.3
        _tag(obj, stable_id, "bone")
        collection.objects.link(obj)
    obj.name = str(value.get("name") or stable_id)
    parent_id = value.get("parentId")
    obj.parent = _find_object(str(parent_id), "bone") if parent_id else root
    if obj.parent is None:
        obj.parent = root
    _apply_transform(obj, value.get("transform") or _identity_transform())


def _upsert_collision(value: dict[str, Any], root: bpy.types.Object, collection: bpy.types.Collection) -> None:
    stable_id = str(value["id"])
    obj = _find_object(stable_id, "collision")
    if obj is None:
        obj = bpy.data.objects.new(str(value.get("name") or stable_id), None)
        obj.empty_display_type = "CUBE"
        obj.empty_display_size = 0.2
        _tag(obj, stable_id, "collision")
        collection.objects.link(obj)
    obj.name = str(value.get("name") or stable_id)
    obj.parent = root
    obj["mcad_collision_kind"] = str(value.get("kind", "mesh"))
    obj["mcad_mesh_ids"] = list(value.get("meshIds") or [])


def _remove_objects(kind: str, stable_ids: list[str]) -> None:
    wanted = {str(value) for value in stable_ids}
    for obj in list(bpy.data.objects):
        if obj.get(_TAG) and obj.get(_KIND) == kind and obj.get(_ID) in wanted:
            if kind == "node":
                node_id = obj.get(_ID)
                for child in list(bpy.data.objects):
                    if child.get(_KIND) == "mesh-instance" and child.get("mcad_node_id") == node_id:
                        bpy.data.objects.remove(child, do_unlink=True)
            bpy.data.objects.remove(obj, do_unlink=True)


def _remove_meshes(stable_ids: list[str]) -> None:
    wanted = {str(value) for value in stable_ids}
    for obj in list(bpy.data.objects):
        if obj.get(_KIND) == "mesh-instance" and obj.get("mcad_mesh_id") in wanted:
            bpy.data.objects.remove(obj, do_unlink=True)
    for mesh in list(bpy.data.meshes):
        if mesh.get(_TAG) and mesh.get(_ID) in wanted:
            bpy.data.meshes.remove(mesh)


def _remove_materials(stable_ids: list[str]) -> None:
    wanted = {str(value) for value in stable_ids}
    for material in list(bpy.data.materials):
        if material.get(_TAG) and material.get(_ID) in wanted:
            bpy.data.materials.remove(material)


def _find_object(stable_id: str, kind: str) -> bpy.types.Object | None:
    for obj in bpy.data.objects:
        if obj.get(_TAG) and obj.get(_ID) == stable_id and obj.get(_KIND) == kind:
            return obj
    return None


def _find_mesh(stable_id: str) -> bpy.types.Mesh | None:
    for mesh in bpy.data.meshes:
        if mesh.get(_TAG) and mesh.get(_ID) == stable_id:
            return mesh
    return None


def _find_material(stable_id: str) -> bpy.types.Material | None:
    for material in bpy.data.materials:
        if material.get(_TAG) and material.get(_ID) == stable_id:
            return material
    return None


def _tag(data: Any, stable_id: str, kind: str) -> None:
    data[_TAG] = True
    data[_ID] = stable_id
    data[_KIND] = kind


def _apply_transform(obj: bpy.types.Object, transform: dict[str, Any]) -> None:
    obj.location = _vector3(transform.get("translation"))
    rotation = transform.get("rotation") or (0.0, 0.0, 0.0, 1.0)
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = (
        float(rotation[3]),
        float(rotation[0]),
        float(rotation[1]),
        float(rotation[2]),
    )
    obj.scale = _vector3(transform.get("scale"), (1.0, 1.0, 1.0))


def _set_input(node: Any, name: str, value: Any) -> None:
    if name in node.inputs:
        node.inputs[name].default_value = value


def _vector3(value: Any, default: tuple[float, float, float] = (0.0, 0.0, 0.0)) -> tuple[float, float, float]:
    if not value or len(value) < 3:
        return default
    return float(value[0]), float(value[1]), float(value[2])


def _color3(value: Any, default: tuple[float, float, float]) -> tuple[float, float, float]:
    return _vector3(value, default)


def _color4(value: Any, default: tuple[float, float, float, float]) -> tuple[float, float, float, float]:
    if not value or len(value) < 4:
        return default
    return float(value[0]), float(value[1]), float(value[2]), float(value[3])


def _identity_transform() -> dict[str, list[float]]:
    return {
        "translation": [0.0, 0.0, 0.0],
        "rotation": [0.0, 0.0, 0.0, 1.0],
        "scale": [1.0, 1.0, 1.0],
    }
