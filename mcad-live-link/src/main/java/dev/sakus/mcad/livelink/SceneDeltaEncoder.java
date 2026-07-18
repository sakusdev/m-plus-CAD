/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

/** Computes deterministic stable-ID upsert/remove messages between generated-scene snapshots. */
public final class SceneDeltaEncoder {
    public record EncodedUpdate(
            String json,
            boolean changed,
            boolean full,
            long revision,
            int upsertCount,
            int removeCount) {
        public EncodedUpdate {
            Objects.requireNonNull(json, "json");
            if (revision < 0L || upsertCount < 0 || removeCount < 0) {
                throw new IllegalArgumentException("counts and revision must be non-negative");
            }
        }
    }

    public EncodedUpdate encode(
            LiveSceneSnapshot previous,
            LiveSceneSnapshot current,
            long revision,
            boolean forceFull) {
        Objects.requireNonNull(current, "current");
        boolean full = forceFull || previous == null || !previous.sceneId().equals(current.sceneId());

        Diff materials = diff(full ? null : previous.materials(), current.materials(), full);
        Diff meshes = diff(full ? null : previous.meshes(), current.meshes(), full);
        Diff nodes = diff(full ? null : previous.nodes(), current.nodes(), full);
        Diff lights = diff(full ? null : previous.lights(), current.lights(), full);
        Diff cameras = diff(full ? null : previous.cameras(), current.cameras(), full);
        Diff curves = diff(full ? null : previous.curves(), current.curves(), full);
        Diff bones = diff(full ? null : previous.bones(), current.bones(), full);
        Diff collisions = diff(full ? null : previous.collisions(), current.collisions(), full);

        int upserts = materials.upserts().size() + meshes.upserts().size() + nodes.upserts().size()
                + lights.upserts().size() + cameras.upserts().size() + curves.upserts().size()
                + bones.upserts().size() + collisions.upserts().size();
        int removals = materials.removals().size() + meshes.removals().size() + nodes.removals().size()
                + lights.removals().size() + cameras.removals().size() + curves.removals().size()
                + bones.removals().size() + collisions.removals().size();
        boolean metadataChanged = full || previous == null
                || !previous.sceneMetadataJson().equals(current.sceneMetadataJson());
        boolean changed = metadataChanged || upserts > 0 || removals > 0;

        String json = "{"
                + "\"protocol\":" + LiveLinkJson.quote(LiveLinkProtocol.NAME) + ','
                + "\"version\":" + LiveLinkProtocol.VERSION + ','
                + "\"type\":\"scene-update\","
                + "\"sceneId\":" + LiveLinkJson.quote(current.sceneId()) + ','
                + "\"revision\":" + revision + ','
                + "\"full\":" + full + ','
                + "\"scene\":" + current.sceneMetadataJson() + ','
                + "\"upsert\":{"
                + field("materials", materials.upserts()) + ','
                + field("meshes", meshes.upserts()) + ','
                + field("nodes", nodes.upserts()) + ','
                + field("lights", lights.upserts()) + ','
                + field("cameras", cameras.upserts()) + ','
                + field("curves", curves.upserts()) + ','
                + field("bones", bones.upserts()) + ','
                + field("collisions", collisions.upserts())
                + "},\"remove\":{"
                + removalField("materials", materials.removals()) + ','
                + removalField("meshes", meshes.removals()) + ','
                + removalField("nodes", nodes.removals()) + ','
                + removalField("lights", lights.removals()) + ','
                + removalField("cameras", cameras.removals()) + ','
                + removalField("curves", curves.removals()) + ','
                + removalField("bones", bones.removals()) + ','
                + removalField("collisions", collisions.removals())
                + "}}";
        return new EncodedUpdate(json, changed, full, revision, upserts, removals);
    }

    public String clearMessage(long revision, String reason) {
        Objects.requireNonNull(reason, "reason");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        return "{"
                + "\"protocol\":" + LiveLinkJson.quote(LiveLinkProtocol.NAME) + ','
                + "\"version\":" + LiveLinkProtocol.VERSION + ','
                + "\"type\":\"scene-clear\","
                + "\"revision\":" + revision + ','
                + "\"reason\":" + LiveLinkJson.quote(reason)
                + '}';
    }

    private static Diff diff(
            NavigableMap<String, String> previous,
            NavigableMap<String, String> current,
            boolean full) {
        List<String> upserts = new ArrayList<>();
        for (var entry : current.entrySet()) {
            if (full || previous == null || !entry.getValue().equals(previous.get(entry.getKey()))) {
                upserts.add(entry.getValue());
            }
        }
        List<String> removals = new ArrayList<>();
        if (!full && previous != null) {
            for (String id : previous.keySet()) {
                if (!current.containsKey(id)) {
                    removals.add(id);
                }
            }
        }
        return new Diff(List.copyOf(upserts), List.copyOf(removals));
    }

    private static String field(String name, List<String> encodedValues) {
        return LiveLinkJson.quote(name) + ':' + LiveLinkJson.array(encodedValues, value -> value);
    }

    private static String removalField(String name, List<String> ids) {
        return LiveLinkJson.quote(name) + ':' + LiveLinkJson.array(ids, LiveLinkJson::quote);
    }

    private record Diff(List<String> upserts, List<String> removals) {
        private Diff {
            Objects.requireNonNull(upserts, "upserts");
            Objects.requireNonNull(removals, "removals");
        }
    }
}
