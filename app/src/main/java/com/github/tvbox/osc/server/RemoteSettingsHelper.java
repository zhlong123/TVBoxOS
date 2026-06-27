package com.github.tvbox.osc.server;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.UiLayoutConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 供手机遥控器读取/修改 TV 端设置（对应设置页 ModelSettingFragment）。
 */
public class RemoteSettingsHelper {

    public static JsonObject getSettings() {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);

        JsonObject values = new JsonObject();
        values.addProperty("api_url", Hawk.get(HawkConfig.API_URL, ""));
        values.addProperty("live_api_url", Hawk.get(HawkConfig.LIVE_API_URL, ""));
        values.addProperty("danmu_api", Hawk.get(HawkConfig.DANMU_API, ""));
        values.addProperty("home_api", Hawk.get(HawkConfig.HOME_API, ""));
        values.addProperty("home_api_name", getHomeSourceName());
        values.addProperty("home_rec", Hawk.get(HawkConfig.HOME_REC, 0));
        values.addProperty("home_rec_style", Hawk.get(HawkConfig.HOME_REC_STYLE, false));
        values.addProperty("default_load_live", Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false));
        values.addProperty("fast_search_mode", Hawk.get(HawkConfig.FAST_SEARCH_MODE, true));
        values.addProperty("search_view", Hawk.get(HawkConfig.SEARCH_VIEW, 0));
        values.addProperty("history_num", Hawk.get(HawkConfig.HISTORY_NUM, 0));
        values.addProperty("play_type", Hawk.get(HawkConfig.PLAY_TYPE, 0));
        values.addProperty("play_render", Hawk.get(HawkConfig.PLAY_RENDER, 0));
        values.addProperty("play_scale", Hawk.get(HawkConfig.PLAY_SCALE, 0));
        values.addProperty("ijk_codec", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"));
        values.addProperty("doh_url", Hawk.get(HawkConfig.DOH_URL, 0));
        values.addProperty("m3u8_purify", Hawk.get(HawkConfig.M3U8_PURIFY, false));
        values.addProperty("auto_switch_line", Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true));
        values.addProperty("show_preview", Hawk.get(HawkConfig.SHOW_PREVIEW, true));
        values.addProperty("ijk_cache_play", Hawk.get(HawkConfig.IJK_CACHE_PLAY, false));
        values.addProperty("danmu_open", DanmuHelper.isOpen());
        values.addProperty("parse_webview", Hawk.get(HawkConfig.PARSE_WEBVIEW, true));
        values.addProperty("ui_card_width", UiLayoutConfig.getCardWidthMm());
        values.addProperty("ui_card_height", UiLayoutConfig.getCardHeightMm());
        values.addProperty("ui_search_card_width", UiLayoutConfig.getSearchCardWidthMm());
        values.addProperty("ui_search_card_height", UiLayoutConfig.getSearchCardHeightMm());
        values.addProperty("ui_grid_span", Hawk.get(HawkConfig.UI_GRID_SPAN, 0));
        values.addProperty("ui_grid_spacing", UiLayoutConfig.getGridSpacingMm());
        values.addProperty("ui_item_margin", UiLayoutConfig.getItemMarginMm());
        values.addProperty("ui_focus_scale", Hawk.get(HawkConfig.UI_FOCUS_SCALE, UiLayoutConfig.DEFAULT_FOCUS_SCALE_PERCENT));
        root.add("values", values);

        JsonObject lists = new JsonObject();
        lists.add("api_history", toJsonArray(Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>())));
        lists.add("home_sources", buildHomeSources());
        lists.add("doh", buildDohList());
        lists.add("ijk_codecs", buildIjkCodecs());
        lists.add("play_types", buildPlayTypes());
        lists.add("play_renders", buildPlayRenders());
        lists.add("play_scales", buildPlayScales());
        lists.add("home_rec_options", buildHomeRecOptions());
        lists.add("search_view_options", buildSearchViewOptions());
        lists.add("history_num_options", buildHistoryNumOptions());
        lists.add("api_lines", buildApiLines());
        root.add("lists", lists);

        return root;
    }

    public static JsonObject apply(String key, String value) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        if (TextUtils.isEmpty(key)) {
            result.addProperty("message", "缺少 key");
            return result;
        }

        try {
            switch (key) {
                case "api_url":
                    return applyApiUrl(value);
                case "live_api_url":
                    return applyLiveApiUrl(value);
                case "danmu_api":
                    Hawk.put(HawkConfig.DANMU_API, TextUtils.isEmpty(value) ? "" : value.trim());
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
                    return ok("弹幕接口已保存");
                case "home_api":
                    return applyHomeSource(value);
                case "home_rec":
                    Hawk.put(HawkConfig.HOME_REC, parseInt(value, 0));
                    return okRefreshHome("首页推荐已更新");
                case "home_rec_style":
                    Hawk.put(HawkConfig.HOME_REC_STYLE, parseBool(value));
                    return ok("首页多行已更新");
                case "default_load_live":
                    Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, parseBool(value));
                    return ok("默认进入页已更新");
                case "fast_search_mode":
                    Hawk.put(HawkConfig.FAST_SEARCH_MODE, parseBool(value));
                    return ok("聚合搜索已更新");
                case "search_view":
                    Hawk.put(HawkConfig.SEARCH_VIEW, parseInt(value, 0));
                    return ok("搜索展示已更新");
                case "history_num":
                    Hawk.put(HawkConfig.HISTORY_NUM, parseInt(value, 0));
                    return ok("历史条数已更新");
                case "play_type":
                    Hawk.put(HawkConfig.PLAY_TYPE, parseInt(value, 0));
                    PlayerHelper.init();
                    return ok("播放器已更新");
                case "play_render":
                    Hawk.put(HawkConfig.PLAY_RENDER, parseInt(value, 0));
                    PlayerHelper.init();
                    return ok("渲染方式已更新");
                case "play_scale":
                    Hawk.put(HawkConfig.PLAY_SCALE, parseInt(value, 0));
                    return ok("画面缩放已更新");
                case "ijk_codec":
                    return applyIjkCodec(value);
                case "doh_url":
                    return applyDoh(parseInt(value, 0));
                case "m3u8_purify":
                    Hawk.put(HawkConfig.M3U8_PURIFY, parseBool(value));
                    return ok("去广告已更新");
                case "auto_switch_line":
                    Hawk.put(HawkConfig.AUTO_SWITCH_LINE, parseBool(value));
                    return ok("自动换线已更新");
                case "show_preview":
                    Hawk.put(HawkConfig.SHOW_PREVIEW, parseBool(value));
                    return ok("窗口预览已更新");
                case "ijk_cache_play":
                    Hawk.put(HawkConfig.IJK_CACHE_PLAY, parseBool(value));
                    return ok("IJK 缓存已更新");
                case "danmu_open":
                    DanmuHelper.setOpen(parseBool(value));
                    return ok("弹幕开关已更新");
                case "parse_webview":
                    Hawk.put(HawkConfig.PARSE_WEBVIEW, parseBool(value));
                    return ok("嗅探 WebView 已更新");
                case "clear_cache":
                    return clearCache();
                case HawkConfig.UI_CARD_WIDTH:
                case HawkConfig.UI_CARD_HEIGHT:
                case HawkConfig.UI_SEARCH_CARD_WIDTH:
                case HawkConfig.UI_SEARCH_CARD_HEIGHT:
                case HawkConfig.UI_GRID_SPAN:
                case HawkConfig.UI_GRID_SPACING:
                case HawkConfig.UI_ITEM_MARGIN:
                case HawkConfig.UI_FOCUS_SCALE:
                    if (UiLayoutConfig.apply(key, value)) {
                        return okRefreshHome("界面布局已更新，返回首页后生效");
                    }
                    return ok("界面布局已更新");
                default:
                    result.addProperty("message", "未知设置项: " + key);
                    return result;
            }
        } catch (Throwable th) {
            result.addProperty("message", th.getMessage() == null ? "保存失败" : th.getMessage());
            return result;
        }
    }

    private static JsonObject applyApiUrl(String value) {
        String api = value == null ? "" : value.trim();
        String oldApi = Hawk.get(HawkConfig.API_URL, "");
        Hawk.put(HawkConfig.API_URL, api);
        if (!HistoryHelper.isApiLineHistory(api)) {
            HistoryHelper.clearApiLineList();
        }
        HistoryHelper.setApiHistory(api);
        JsonObject result = ok("点播配置已保存");
        if (!api.equals(oldApi)) {
            result.addProperty("restart", true);
            scheduleRestart("配置已切换，即将重启应用");
        }
        return result;
    }

    private static JsonObject applyLiveApiUrl(String value) {
        String api = value == null ? "" : value.trim();
        Hawk.put(HawkConfig.LIVE_API_URL, api);
        HistoryHelper.setLiveApiHistory(api);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_LIVE_API_URL_CHANGE, api));
        return ok("直播配置已保存");
    }

    private static JsonObject applyHomeSource(String sourceKey) {
        if (TextUtils.isEmpty(sourceKey)) {
            JsonObject bad = new JsonObject();
            bad.addProperty("ok", false);
            bad.addProperty("message", "站源 key 不能为空");
            return bad;
        }
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        for (SourceBean bean : sites) {
            if (sourceKey.equals(bean.getKey())) {
                ApiConfig.get().setSourceBean(bean);
                JsonObject result = okRefreshHome("首页站源已切换为 " + bean.getName());
                result.addProperty("restart", true);
                scheduleRestart("首页站源已切换，即将重启应用");
                return result;
            }
        }
        JsonObject bad = new JsonObject();
        bad.addProperty("ok", false);
        bad.addProperty("message", "未找到站源");
        return bad;
    }

    private static JsonObject applyIjkCodec(String name) {
        List<IJKCode> codes = ApiConfig.get().getIjkCodes();
        if (codes != null) {
            for (IJKCode code : codes) {
                if (name != null && name.equals(code.getName())) {
                    code.selected(true);
                    return ok("IJK 解码已更新");
                }
            }
        }
        Hawk.put(HawkConfig.IJK_CODEC, name);
        return ok("IJK 解码已更新");
    }

    private static JsonObject applyDoh(int pos) {
        if (pos < 0 || pos >= OkGoHelper.dnsHttpsList.size()) {
            pos = 0;
        }
        Hawk.put(HawkConfig.DOH_URL, pos);
        OkGoHelper.reloadDns();
        IjkMediaPlayer.toggleDotPort(pos > 0);
        return ok("安全 DNS 已更新");
    }

    private static JsonObject clearCache() {
        JsonObject result = ok("缓存清理中…");
        result.addProperty("restart", true);
        new Thread(() -> {
            try {
                String cachePath = FileUtils.getCachePath();
                File cacheDir = new File(cachePath);
                String cspCachePath = FileUtils.getFilePath() + "/csp/";
                File cspCacheDir = new File(cspCachePath);
                ApiConfig.get().clearSpiderCache();
                if (cacheDir.exists()) {
                    FileUtils.cleanDirectory(cacheDir);
                }
                if (cspCacheDir.exists()) {
                    FileUtils.cleanDirectory(cspCacheDir);
                }
            } catch (Exception ignored) {
            } finally {
                scheduleRestart("缓存已清空，即将重启");
            }
        }).start();
        return result;
    }

    private static JsonObject ok(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("ok", true);
        obj.addProperty("message", message);
        obj.addProperty("restart", false);
        obj.addProperty("refreshHome", false);
        return obj;
    }

    private static JsonObject okRefreshHome(String message) {
        JsonObject obj = ok(message);
        obj.addProperty("refreshHome", true);
        return obj;
    }

    private static void scheduleRestart(String toast) {
        Context context = App.getInstance();
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show());
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
                System.exit(0);
            }
        }, 2000);
    }

    private static boolean parseBool(String value) {
        if (value == null) return false;
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String getHomeSourceName() {
        try {
            return ApiConfig.get().getHomeSourceBean().getName();
        } catch (Throwable th) {
            return "";
        }
    }

    private static JsonArray toJsonArray(List<String> items) {
        JsonArray arr = new JsonArray();
        if (items != null) {
            for (String item : items) {
                arr.add(item);
            }
        }
        return arr;
    }

    private static JsonArray buildHomeSources() {
        JsonArray arr = new JsonArray();
        try {
            for (SourceBean bean : ApiConfig.get().getSwitchSourceBeanList()) {
                JsonObject item = new JsonObject();
                item.addProperty("key", bean.getKey());
                item.addProperty("name", bean.getName());
                arr.add(item);
            }
        } catch (Throwable ignored) {
        }
        return arr;
    }

    private static JsonArray buildDohList() {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < OkGoHelper.dnsHttpsList.size(); i++) {
            JsonObject item = new JsonObject();
            item.addProperty("value", i);
            item.addProperty("label", OkGoHelper.dnsHttpsList.get(i));
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildIjkCodecs() {
        JsonArray arr = new JsonArray();
        try {
            List<IJKCode> codes = ApiConfig.get().getIjkCodes();
            if (codes != null) {
                for (IJKCode code : codes) {
                    arr.add(code.getName());
                }
            }
        } catch (Throwable ignored) {
        }
        if (arr.size() == 0) {
            arr.add("硬解码");
        }
        return arr;
    }

    private static JsonArray buildPlayTypes() {
        JsonArray arr = new JsonArray();
        for (Integer type : PlayerHelper.getExistPlayerTypes()) {
            JsonObject item = new JsonObject();
            item.addProperty("value", type);
            item.addProperty("label", PlayerHelper.getPlayerName(type));
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildPlayRenders() {
        JsonArray arr = new JsonArray();
        for (int i = 0; i <= 1; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("value", i);
            item.addProperty("label", PlayerHelper.getRenderName(i));
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildPlayScales() {
        JsonArray arr = new JsonArray();
        for (int i = 0; i <= 5; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("value", i);
            item.addProperty("label", PlayerHelper.getScaleName(i));
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildHomeRecOptions() {
        JsonArray arr = new JsonArray();
        String[] labels = {"豆瓣热播", "站点推荐", "观看历史"};
        for (int i = 0; i < labels.length; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("value", i);
            item.addProperty("label", labels[i]);
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildSearchViewOptions() {
        JsonArray arr = new JsonArray();
        String[] labels = {"文字列表", "缩略图"};
        for (int i = 0; i < labels.length; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("value", i);
            item.addProperty("label", labels[i]);
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildHistoryNumOptions() {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < 3; i++) {
            JsonObject item = new JsonObject();
            item.addProperty("value", i);
            item.addProperty("label", HistoryHelper.getHistoryNumName(i));
            arr.add(item);
        }
        return arr;
    }

    private static JsonArray buildApiLines() {
        JsonArray arr = new JsonArray();
        ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        for (String line : lines) {
            JsonObject item = new JsonObject();
            item.addProperty("raw", line);
            item.addProperty("name", HistoryHelper.getApiLineName(line));
            item.addProperty("url", HistoryHelper.getApiLineUrl(line));
            arr.add(item);
        }
        return arr;
    }
}
