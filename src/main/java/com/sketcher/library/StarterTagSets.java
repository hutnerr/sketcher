package com.sketcher.library;

import com.google.gson.Gson;
import com.sketcher.db.dao.TagDao;
import com.sketcher.model.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional starter tag vocabularies (PLAN.md 1.5): the app ships with no
 * built-in tags, but offers a few ready-made sets the user can import with
 * one click to soften the cold start.
 */
public class StarterTagSets {
    private static final Gson GSON = new Gson();

    public static final Map<String, String> AVAILABLE = Map.of(
            "figure-study", "Figure study",
            "animals", "Animals",
            "environments", "Environments"
    );

    /** Creates any missing tags along each slash-separated path; existing ones are reused. */
    public static int apply(String key, TagDao tagDao) {
        String resourcePath = "/tagsets/" + key + ".json";
        List<String> paths;
        try (InputStream in = StarterTagSets.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("Unknown starter tag set: " + key);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            paths = List.of(GSON.fromJson(json, String[].class));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load starter tag set " + key, e);
        }

        int created = 0;
        Map<String, Integer> resolvedByPath = new LinkedHashMap<>();
        for (String path : paths) {
            String[] parts = path.split("/");
            Integer parentId = null;
            StringBuilder soFar = new StringBuilder();
            for (String part : parts) {
                soFar.append('/').append(part);
                String key2 = soFar.toString();
                Integer id = resolvedByPath.get(key2);
                if (id == null) {
                    id = findChildByName(tagDao, parentId, part);
                    if (id == null) {
                        id = tagDao.create(part, parentId);
                        created++;
                    }
                    resolvedByPath.put(key2, id);
                }
                parentId = id;
            }
        }
        return created;
    }

    private static Integer findChildByName(TagDao tagDao, Integer parentId, String name) {
        for (Tag t : tagDao.listChildren(parentId)) {
            if (t.name().equalsIgnoreCase(name)) return t.id();
        }
        return null;
    }
}
