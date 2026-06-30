package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Movie;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * TVBox/CatVod 协议：folder / cover 为分类导航，file 或空为可播放正片。
 */
public final class VodTagHelper {

    private VodTagHelper() {
    }

    public static String normalizeTag(String vodTag, JsonElement cate) {
        if (vodTag != null && !vodTag.trim().isEmpty()) {
            return vodTag.trim();
        }
        if (hasCate(cate)) {
            return "folder";
        }
        return vodTag;
    }

    public static boolean hasCate(JsonElement cate) {
        if (cate == null || cate.isJsonNull()) {
            return false;
        }
        if (cate.isJsonObject()) {
            return cate.getAsJsonObject().size() > 0;
        }
        return cate.isJsonPrimitive() && cate.getAsJsonPrimitive().isString()
                && !cate.getAsJsonPrimitive().getAsString().trim().isEmpty();
    }

    public static boolean isCategoryNavigationEntry(Movie.Video video) {
        if (video == null || video.tag == null) {
            return false;
        }
        String tag = video.tag.trim();
        return "cover".equalsIgnoreCase(tag) || "folder".equalsIgnoreCase(tag);
    }

    public static List<Movie.Video> excludeCategoryNavigationEntries(List<Movie.Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return videos;
        }
        List<Movie.Video> result = new ArrayList<>(videos.size());
        for (Movie.Video video : videos) {
            if (video != null && !isCategoryNavigationEntry(video)) {
                result.add(video);
            }
        }
        return result;
    }
}
