//QuoteMeme名言作图Next v2.3
//项目链接:https://github.com/YiJieqwq/QuoteMemeNext
//基于MIT协议开源

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

final Object lock = new Object();

// ----------------- 头像延迟删除 -----------------
static Map avatarDeleteTasks = new HashMap();
static Handler avatarHandler = new Handler(Looper.getMainLooper());

void cancelAvatarDeletion(String userId) {
    Runnable oldTask = (Runnable) avatarDeleteTasks.remove(userId);
    if (oldTask != null) avatarHandler.removeCallbacks(oldTask);
}

void scheduleAvatarDeletion(String userId, String localPath) {
    cancelAvatarDeletion(userId);
    Runnable deleteTask = new Runnable() {
        public void run() {
            new File(localPath).delete();
            avatarDeleteTasks.remove(userId);
        }
    };
    avatarDeleteTasks.put(userId, deleteTask);
    avatarHandler.postDelayed(deleteTask, 10000);
}

// ----------------- 全局开关 -----------------
boolean isQuoteCmdEnabled() {
    return getInt(pluginId, "global_quote_cmd_enabled", 1) == 1;
}
void setQuoteCmdEnabled(boolean enabled) {
    putInt(pluginId, "global_quote_cmd_enabled", enabled ? 1 : 0);
}

boolean isAllowOthersCmd() {
    return getInt(pluginId, "global_allow_others_cmd", 0) == 1;
}
void setAllowOthersCmd(boolean enabled) {
    putInt(pluginId, "global_allow_others_cmd", enabled ? 1 : 0);
}

// ----------------- 全局颜色存储 -----------------
int getGlobalColor(String key, int def) {
    return getInt(pluginId, "global_" + key, def);
}
void putGlobalColor(String key, int val) {
    putInt(pluginId, "global_" + key, val);
}

// ----------------- 辅助工具 -----------------
void showEasyDialog(String text) {
    Activity act = getNowActivity();
    if (act != null) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    new AlertDialog.Builder(act)
                        .setMessage(text)
                        .setPositiveButton("关闭", null)
                        .show();
                } catch (Exception e) {}
            }
        });
    } else {
        qqToast(1, text);
    }
}

String getMemberName(int type, String peerUin, String uin) {
    if (type == 2) {
        try {
            Object mem = getMemberInfo(peerUin, uin);
            if (mem != null && mem.uinName != null) return mem.uinName;
        } catch(Exception e) {}
    } else if (type == 1) {
        try {
            java.util.List list = getAllFriend();
            for (Object f : list) {
                if (String.valueOf(f.uin).equals(uin)) {
                    return (f.remark != null && f.remark.length() > 0) ? f.remark : f.name;
                }
            }
        } catch(Exception e) {}
    }
    return uin;
}

Object getFieldValue(Object obj, String fieldName) {
    if (obj == null) return null;
    try {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    } catch (NoSuchFieldException e) {
        Class clazz = obj.getClass().getSuperclass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ex) {}
            clazz = clazz.getSuperclass();
        }
    } catch (Exception e) {}
    return null;
}

String getStringField(Object obj, String fieldName) {
    try {
        Field f = obj.getClass().getField(fieldName);
        f.setAccessible(true);
        Object val = f.get(obj);
        if (val != null) return val.toString();
    } catch (Exception e) {}
    return null;
}

Long getLongField(Object obj, String fieldName) {
    try {
        Field f = obj.getClass().getField(fieldName);
        f.setAccessible(true);
        Object val = f.get(obj);
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        if (val != null) return Long.parseLong(val.toString());
    } catch (Exception e) {}
    return null;
}

int getIntField(Object obj, String fieldName) {
    try {
        Field f = obj.getClass().getField(fieldName);
        f.setAccessible(true);
        return f.getInt(obj);
    } catch (Exception e) {}
    return -1;
}

// ----------------- 消息监听 / 指令处理 -----------------
public void onMsg(Object msg) {
    synchronized (lock) {
        try {
            if (!isQuoteCmdEnabled()) return;
            String text = msg.msg;
            if (text != null && text.trim().equals("/名言")) {
                if (!isAllowOthersCmd()) {
                    String senderUin = String.valueOf(msg.userUin);
                    if (!senderUin.equals(myUin)) return;
                }
                processQuoteCommand(msg);
            }
        } catch (Exception e) {}
    }
}

void processQuoteCommand(Object msgData) {
    Activity act = getNowActivity();
    if (act == null) return;

    String peerUin = String.valueOf(msgData.peerUin);
    int chatType = msgData.type;
    sendMsg(peerUin, "正在生成名言...", chatType);

    String pureText = null;
    String repliedUserId = null;
    boolean isImageMode = false;

    try {
        Object raw = getFieldValue(msgData, "data");
        if (raw != null) {
            List elements = (List) getFieldValue(raw, "elements");
            if (elements != null) {
                for (Object el : elements) {
                    Object replyEl = getFieldValue(el, "replyElement");
                    if (replyEl == null) continue;

                    repliedUserId = getStringField(replyEl, "senderUin");
                    if (repliedUserId == null || repliedUserId.isEmpty()) {
                        repliedUserId = getStringField(replyEl, "uin");
                    }
                    if (repliedUserId == null || repliedUserId.isEmpty()) {
                        String uid = getStringField(replyEl, "senderUidStr");
                        if (uid != null && !uid.isEmpty()) {
                            String uin = getUinFromUid(uid);
                            if (uin != null && !uin.isEmpty()) repliedUserId = uin;
                        }
                    }

                    Object srcText = getFieldValue(replyEl, "sourceMsgText");
                    if (srcText != null) pureText = srcText.toString();

                    Object textElems = getFieldValue(replyEl, "sourceMsgTextElems");
                    if (textElems instanceof List && !((List) textElems).isEmpty()) {
                        Object firstElem = ((List) textElems).get(0);
                        int type = getIntField(firstElem, "replyAbsElemType");
                        if (type == 3) isImageMode = true;
                    }

                    if (isImageMode) {
                        Long replyMsgId = getLongField(replyEl, "replayMsgId");
                        if (replyMsgId != null && replyMsgId != 0) {
                            String picUrl = findPicUrlByMsgId(act, replyMsgId.longValue());
                            if (picUrl != null && !picUrl.isEmpty()) pureText = picUrl;
                        }
                    }

                    // ★ 最终清洗：删除第一个冒号及前面的所有内容
                    if (pureText != null && !isImageMode) {
                        int colonIdx = pureText.indexOf(":");
                        int chineseColonIdx = pureText.indexOf("：");
                        int idx = -1;
                        if (colonIdx != -1 && chineseColonIdx != -1) {
                            idx = Math.min(colonIdx, chineseColonIdx);
                        } else if (colonIdx != -1) {
                            idx = colonIdx;
                        } else if (chineseColonIdx != -1) {
                            idx = chineseColonIdx;
                        }
                        if (idx != -1) {
                            pureText = pureText.substring(idx + 1).trim();
                        }
                    }

                    break;
                }
            }
        }
    } catch (Exception e) {}

    if (pureText == null || pureText.isEmpty()) {
        sendMsg(peerUin, "未找到被回复的消息内容", chatType);
        return;
    }
    if (repliedUserId == null || repliedUserId.isEmpty()) repliedUserId = "0";

    String name = getMemberName(chatType, peerUin, repliedUserId);
    generateQuoteImage(peerUin, repliedUserId, pureText, name, chatType);
}

String findPicUrlByMsgId(Activity act, long msgId) {
    RecyclerView rv = findChatRecyclerView(act);
    if (rv == null) return null;
    RecyclerView.Adapter adapter = rv.getAdapter();
    if (adapter == null) return null;

    Object differ = getFieldValue(adapter, "m");
    if (differ == null) return null;

    List msgList = (List) getFieldValue(differ, "d");
    if (msgList == null) return null;

    for (int i = 0; i < msgList.size(); i++) {
        Object item = msgList.get(i);
        if (item == null) continue;
        Object record = getFieldValue(item, "e");
        if (record == null) continue;
        Long mid = getLongField(record, "msgId");
        if (mid == null || mid.longValue() != msgId) continue;

        List elements = (List) getFieldValue(record, "elements");
        if (elements == null) continue;
        for (Object el : elements) {
            Object picEl = getFieldValue(el, "picElement");
            if (picEl == null) continue;

            String url = extractAnyStringFromPicElement(picEl);
            if (url != null) return url;

            Object textEl = getFieldValue(el, "textElement");
            if (textEl != null) {
                String content = getStringField(textEl, "content");
                if (content != null) {
                    String ext = extractImageUrl(content);
                    if (!content.equals(ext)) return ext;
                }
            }
        }
        String fullText = safeGetMsgRecordText(record);
        String ext = extractImageUrl(fullText);
        if (!fullText.equals(ext)) return ext;
        break;
    }
    return null;
}

String extractAnyStringFromPicElement(Object picEl) {
    if (picEl == null) return null;
    Class clazz = picEl.getClass();
    while (clazz != null && clazz != Object.class) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field f : fields) {
            if (f.getType() != String.class) continue;
            try {
                f.setAccessible(true);
                String val = (String) f.get(picEl);
                if (val == null || val.isEmpty()) continue;
                if (val.startsWith("http://") || val.startsWith("https://")) {
                    if (val.contains(".jpg") || val.contains(".jpeg") || val.contains(".png") ||
                        val.contains(".gif") || val.contains(".bmp") || val.contains(".webp") ||
                        val.contains("pic") || val.contains("image")) return val;
                }
                if (val.startsWith("/") && (val.contains(".jpg") || val.contains(".jpeg") || val.contains(".png") ||
                    val.contains(".gif") || val.contains(".bmp") || val.contains(".webp"))) {
                    File file = new File(val);
                    if (file.exists()) return "file://" + val;
                }
                if (val.toLowerCase().contains("source") || val.toLowerCase().contains("url") ||
                    val.toLowerCase().contains("path") || val.toLowerCase().contains("thumb")) return val;
            } catch (Exception e) {}
        }
        clazz = clazz.getSuperclass();
    }
    return null;
}

String safeGetMsgRecordText(Object record) {
    if (record == null) return "";
    StringBuilder sb = new StringBuilder();
    List elements = (List) getFieldValue(record, "elements");
    if (elements != null) {
        for (Object el : elements) {
            Object textEl = getFieldValue(el, "textElement");
            if (textEl != null) {
                Object content = getFieldValue(textEl, "content");
                if (content != null) sb.append(content.toString());
            }
        }
    }
    return sb.toString();
}

// ----------------- 悬浮菜单 -----------------
addItem("允许使用\"/名言\"指令做图 开/关", "toggleQuoteCmd");
void toggleQuoteCmd(int chatType, String peerUin, String name) {
    boolean current = isQuoteCmdEnabled();
    setQuoteCmdEnabled(!current);
    qqToast(2, "指令做图已" + (!current ? "开启" : "关闭"));
}

addItem("允许他人使用\"/名言\"指令 开/关", "toggleAllowOthersCmd");
void toggleAllowOthersCmd(int chatType, String peerUin, String name) {
    boolean current = isAllowOthersCmd();
    setAllowOthersCmd(!current);
    qqToast(2, "允许他人指令已" + (current ? "关闭" : "开启"));
}

addItem("配置名言做图各元素颜色", "openColorConfig");
void openColorConfig(int chatType, String peerUin, String name) {
    final Activity act = getNowActivity();
    if (act == null) return;
    new Handler(Looper.getMainLooper()).post(new Runnable() {
        public void run() { showAllColorDialog(act); }
    });
}

void showAllColorDialog(final Activity act) {
    int textR = getGlobalColor("text_R", 255);
    int textG = getGlobalColor("text_G", 255);
    int textB = getGlobalColor("text_B", 255);
    int bgR = getGlobalColor("bg_R", 0);
    int bgG = getGlobalColor("bg_G", 0);
    int bgB = getGlobalColor("bg_B", 0);
    int ovA = getGlobalColor("overlay_alpha", 255);
    int ovR = getGlobalColor("overlay_R", 0);
    int ovG = getGlobalColor("overlay_G", 0);
    int ovB = getGlobalColor("overlay_B", 0);

    ScrollView scrollView = new ScrollView(act);
    LinearLayout container = new LinearLayout(act);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setPadding(70, 60, 70, 60);
    GradientDrawable rootBg = new GradientDrawable();
    rootBg.setShape(GradientDrawable.RECTANGLE);
    rootBg.setCornerRadius(45);
    rootBg.setColor(Color.WHITE);
    container.setBackground(rootBg);

    TextView title = new TextView(act);
    title.setText("名言作图配色");
    title.setTextSize(20);
    title.setTextColor(Color.parseColor("#1A1A1A"));
    title.getPaint().setFakeBoldText(true);
    title.setGravity(Gravity.CENTER);
    container.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    container.addView(makeSectionTitle(act, "▎文字颜色"));
    container.addView(makeSubtitle(act, "RGB用英文逗号分隔，0~255"));
    final EditText etText = new EditText(act);
    etText.setText(textR + "," + textG + "," + textB);
    etText.setHint("255,255,255");
    styleEditText(act, etText);
    container.addView(etText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    container.addView(makeSectionTitle(act, "▎底层背景颜色"));
    container.addView(makeSubtitle(act, "RGB用英文逗号分隔，0~255"));
    final EditText etBg = new EditText(act);
    etBg.setText(bgR + "," + bgG + "," + bgB);
    etBg.setHint("0,0,0");
    styleEditText(act, etBg);
    container.addView(etBg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    container.addView(makeSectionTitle(act, "▎渐变遮罩"));
    container.addView(makeSubtitle(act, "透明度,R,G,B 用英文逗号分隔，0~255"));
    final EditText etOv = new EditText(act);
    etOv.setText(ovA + "," + ovR + "," + ovG + "," + ovB);
    etOv.setHint("255,0,0,0");
    styleEditText(act, etOv);
    container.addView(etOv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    LinearLayout buttonBar = new LinearLayout(act);
    buttonBar.setOrientation(LinearLayout.HORIZONTAL);
    buttonBar.setPadding(0, 40, 0, 0);

    Button cancelBtn = new Button(act);
    cancelBtn.setText("取消");
    cancelBtn.setTextSize(15);
    cancelBtn.setTextColor(Color.parseColor("#555555"));
    GradientDrawable btnCancelBg = new GradientDrawable();
    btnCancelBg.setCornerRadius(25);
    btnCancelBg.setColor(Color.parseColor("#F0F0F0"));
    cancelBtn.setBackground(btnCancelBg);

    Button saveBtn = new Button(act);
    saveBtn.setText("保存全部");
    saveBtn.setTextSize(15);
    saveBtn.setTextColor(Color.WHITE);
    saveBtn.getPaint().setFakeBoldText(true);
    GradientDrawable btnSaveBg = new GradientDrawable();
    btnSaveBg.setCornerRadius(25);
    btnSaveBg.setColor(Color.parseColor("#00CAFC"));
    saveBtn.setBackground(btnSaveBg);

    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, 110, 1.0f);
    buttonBar.addView(cancelBtn, btnParams);
    View spacer = new View(act);
    buttonBar.addView(spacer, new LinearLayout.LayoutParams(40, ViewGroup.LayoutParams.MATCH_PARENT));
    buttonBar.addView(saveBtn, btnParams);
    container.addView(buttonBar);

    scrollView.addView(container);
    final Dialog dialog = new Dialog(act);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.setContentView(scrollView);
    Window window = dialog.getWindow();
    if (window != null) {
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = (int) (act.getResources().getDisplayMetrics().widthPixels * 0.9);
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.dimAmount = 0.5f;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }
    dialog.show();

    cancelBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { dialog.dismiss(); }
    });
    saveBtn.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
            try {
                String[] textParts = etText.getText().toString().trim().split(",");
                String[] bgParts = etBg.getText().toString().trim().split(",");
                String[] ovParts = etOv.getText().toString().trim().split(",");
                if (textParts.length != 3 || bgParts.length != 3 || ovParts.length != 4) {
                    qqToast(1, "请检查输入格式");
                    return;
                }
                int tr = Integer.parseInt(textParts[0].trim()), tg = Integer.parseInt(textParts[1].trim()), tb = Integer.parseInt(textParts[2].trim());
                int br = Integer.parseInt(bgParts[0].trim()), bg = Integer.parseInt(bgParts[1].trim()), bb = Integer.parseInt(bgParts[2].trim());
                int oa = Integer.parseInt(ovParts[0].trim()), or = Integer.parseInt(ovParts[1].trim()), og = Integer.parseInt(ovParts[2].trim()), ob = Integer.parseInt(ovParts[3].trim());
                if (tr<0||tr>255||tg<0||tg>255||tb<0||tb>255||br<0||br>255||bg<0||bg>255||bb<0||bb>255||oa<0||oa>255||or<0||or>255||og<0||og>255||ob<0||ob>255) {
                    qqToast(1, "数值超出0~255范围");
                    return;
                }
                putGlobalColor("text_R", tr); putGlobalColor("text_G", tg); putGlobalColor("text_B", tb);
                putGlobalColor("bg_R", br); putGlobalColor("bg_G", bg); putGlobalColor("bg_B", bb);
                putGlobalColor("overlay_alpha", oa); putGlobalColor("overlay_R", or); putGlobalColor("overlay_G", og); putGlobalColor("overlay_B", ob);
                qqToast(2, "所有配色已保存");
                dialog.dismiss();
            } catch (NumberFormatException e) {
                qqToast(1, "数字格式错误");
            }
        }
    });
}

TextView makeSectionTitle(android.content.Context ctx, String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(16);
    tv.setTextColor(Color.parseColor("#333333"));
    tv.getPaint().setFakeBoldText(true);
    tv.setPadding(0, 30, 0, 5);
    return tv;
}

TextView makeSubtitle(android.content.Context ctx, String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(12);
    tv.setTextColor(Color.parseColor("#999999"));
    tv.setPadding(0, 0, 0, 10);
    return tv;
}

void styleEditText(android.content.Context ctx, EditText et) {
    et.setTextSize(15);
    et.setTextColor(Color.parseColor("#333333"));
    et.setHintTextColor(Color.parseColor("#A8A8A8"));
    et.setPadding(30, 25, 30, 25);
    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.RECTANGLE);
    bg.setCornerRadius(20);
    bg.setColor(Color.parseColor("#F5F6FA"));
    et.setBackground(bg);
}

// ----------------- 长按菜单 – 生成名言 -----------------
addMenuItem("生成名言", "generateQuote");
public void generateQuote(final Object msg) {
    synchronized (lock) {
        try {
            String text = msg.msg;
            if (text == null || text.trim().length() == 0) return;

            if (extractImageUrl(text).equals(text)) {
                String picUrl = extractPicUrlFromMsgData(msg);
                if (picUrl != null && !picUrl.isEmpty()) text = picUrl;
            }

            String userId = String.valueOf(msg.userUin);
            String peerUin = String.valueOf(msg.peerUin);
            int msgType = msg.type;
            String name = getMemberName(msgType, peerUin, userId);
            qqToast(2, "正在生成名言...");
            generateQuoteImage(peerUin, userId, text, name, msgType);
        } catch (Exception e) {}
    }
}

String extractPicUrlFromMsgData(Object msgData) {
    try {
        Object raw = getFieldValue(msgData, "data");
        if (raw != null) {
            List elements = (List) getFieldValue(raw, "elements");
            if (elements != null) {
                for (Object el : elements) {
                    Object picEl = getFieldValue(el, "picElement");
                    if (picEl != null) {
                        String url = extractAnyStringFromPicElement(picEl);
                        if (url != null) return url;
                    }
                    Object textEl = getFieldValue(el, "textElement");
                    if (textEl != null) {
                        String content = getStringField(textEl, "content");
                        if (content != null) {
                            String ext = extractImageUrl(content);
                            if (!content.equals(ext)) return ext;
                        }
                    }
                }
            }
        }
    } catch (Exception e) {}
    return null;
}

void generateQuoteImage(String peerUin, String userId, String rawText, String name, int msgType) {
    new Thread(new Runnable() {
        public void run() {
            synchronized (lock) {
                String localPath = "";
                try {
                    cancelAvatarDeletion(userId);

                    String textUrl = extractImageUrl(rawText);
                    boolean isTextMode = rawText.equals(textUrl);

                    int R = getGlobalColor("text_R", 255);
                    int G = getGlobalColor("text_G", 255);
                    int B = getGlobalColor("text_B", 255);

                    String fontPath = getString(pluginId, "global_font_path", null);
                    Typeface typeface = (fontPath != null) ? getTypefaceFromPath(fontPath) : Typeface.DEFAULT;
                    String fontName = getString(pluginId, "global_font_name", null);
                    if (fontName != null) typeface = getTypefaceFromFile(fontName);

                    String imageUrl = "https://q1.qlogo.cn/g?b=qq&nk=" + userId + "&s=640";
                    localPath = pluginPath + "/CachePics/" + userId + ".png";
                    File file = new File(localPath);
                    if (file.exists() && file.length() < 100) file.delete();
                    if (!file.exists()) {
                        file.getParentFile().mkdirs();
                        downloadImage(imageUrl, localPath);
                    }

                    Bitmap resultBitmap;
                    if (isTextMode) {
                        resultBitmap = generateImageBitmap(rawText, localPath, typeface, R, G, B, name);
                    } else {
                        resultBitmap = handleNetworkImageMode(textUrl, localPath);
                    }

                    if (resultBitmap != null) {
                        String resultImagePath = pluginPath + "/tmp_quote_" + System.currentTimeMillis() + ".png";
                        saveBitmapToFile(resultBitmap, resultImagePath);
                        sendPic(peerUin, resultImagePath, msgType);
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() { new File(resultImagePath).delete(); }
                        }, 5000);
                        resultBitmap.recycle();
                    } else {
                        showEasyDialog("生成失败：图片解析异常");
                    }
                } catch (Exception e) {
                    showEasyDialog("生成图片失败: " + e.getMessage());
                } finally {
                    if (!localPath.isEmpty()) scheduleAvatarDeletion(userId, localPath);
                }
            }
        }
    }).start();
}

String extractImageUrl(String text) {
    if (text == null) return "";
    String lower = text.toLowerCase();
    if (lower.contains("[picurl=") && text.contains("]")) {
        int start = lower.indexOf("[picurl=") + 8;
        int end = text.indexOf("]", start);
        if (end > start) return text.substring(start, end);
    } else if (lower.contains("url=") && text.contains("]")) {
        int start = lower.indexOf("url=") + 4;
        int end = text.indexOf("]", start);
        if (end > start) return text.substring(start, end);
    } else if (lower.contains("[pic=") && text.contains("]")) {
        int start = lower.indexOf("[pic=") + 5;
        int end = text.indexOf("]", start);
        if (end > start) return text.substring(start, end);
    } else if (lower.startsWith("http")) return text;
    return text;
}

Bitmap generateImageBitmap(String text, String localPath, Typeface typeface, int R, int G, int B, String userName) {
    try {
        int bgR = getGlobalColor("bg_R", 0);
        int bgG = getGlobalColor("bg_G", 0);
        int bgB = getGlobalColor("bg_B", 0);
        int ovAlpha = getGlobalColor("overlay_alpha", 255);
        int ovR = getGlobalColor("overlay_R", 0);
        int ovG = getGlobalColor("overlay_G", 0);
        int ovB = getGlobalColor("overlay_B", 0);

        Bitmap resultBitmap = Bitmap.createBitmap(1280, 640, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawColor(Color.rgb(bgR, bgG, bgB));

        Bitmap leftImage = BitmapFactory.decodeFile(localPath);
        if (leftImage == null) {
            new File(localPath).delete();
            leftImage = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            leftImage.eraseColor(Color.DKGRAY);
        }
        Bitmap scaledLeft = Bitmap.createScaledBitmap(leftImage, 640, 640, true);
        canvas.drawBitmap(scaledLeft, 0, 0, null);
        scaledLeft.recycle();
        leftImage.recycle();

        if (ovAlpha > 0) {
            Paint gradientPaint = new Paint();
            int[] colors = new int[]{ Color.argb(0, ovR, ovG, ovB), Color.argb(ovAlpha, ovR, ovG, ovB) };
            float[] positions = new float[]{ 0.0f, 1.0f };
            LinearGradient gradient = new LinearGradient(0, 0, 640, 0, colors, positions, Shader.TileMode.CLAMP);
            gradientPaint.setShader(gradient);
            canvas.drawRect(0, 0, 1280, 640, gradientPaint);
        }

        int fontSize = 40;
        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.rgb(R, G, B));
        textPaint.setTextSize(fontSize);
        textPaint.setTypeface(typeface);
        textPaint.setAntiAlias(true);

        float maxWidth = 500f;
        StaticLayout staticLayout = createTextLayout(text, textPaint, (int) maxWidth);
        while (staticLayout.getHeight() > 400 && fontSize > 10) {
            fontSize--;
            textPaint.setTextSize(fontSize);
            staticLayout = createTextLayout(text, textPaint, (int) maxWidth);
        }

        canvas.save();
        canvas.translate(660f, 100f);
        staticLayout.draw(canvas);
        canvas.restore();

        String bottomText = "———  " + userName;
        Paint bottomTextPaint = new Paint();
        bottomTextPaint.setColor(Color.rgb(R, G, B));
        bottomTextPaint.setTextAlign(Paint.Align.RIGHT);
        bottomTextPaint.setTextSize(30f);
        bottomTextPaint.setTypeface(typeface);
        bottomTextPaint.setAntiAlias(true);
        canvas.drawText(bottomText, 1200f, 550f, bottomTextPaint);

        return resultBitmap;
    } catch (Exception e) {
        showEasyDialog("画图模块异常: " + e.getMessage());
        return null;
    }
}

Bitmap handleNetworkImageMode(String text, String localPath) {
    InputStream is = null;
    try {
        if (text.startsWith("http://")) text = text.replaceFirst("http://", "https://");
        URL textImageUrl = new URL(text);
        HttpURLConnection conn = (HttpURLConnection) textImageUrl.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.connect();

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            is = conn.getInputStream();
            Bitmap textImage = BitmapFactory.decodeStream(is);
            if (textImage != null) {
                int scaledWidth = (int) ((640.0 / textImage.getHeight()) * textImage.getWidth());
                Bitmap scaledTextImage = Bitmap.createScaledBitmap(textImage, scaledWidth, 640, true);
                int imageWidth = 640 + scaledWidth;
                Bitmap resultBitmap = Bitmap.createBitmap(imageWidth, 640, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(resultBitmap);
                canvas.drawColor(Color.rgb(getGlobalColor("bg_R", 0), getGlobalColor("bg_G", 0), getGlobalColor("bg_B", 0)));

                Bitmap leftImage = BitmapFactory.decodeFile(localPath);
                if (leftImage == null) {
                    leftImage = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
                    leftImage.eraseColor(Color.DKGRAY);
                }
                leftImage = Bitmap.createScaledBitmap(leftImage, 640, 640, true);
                canvas.drawBitmap(leftImage, 0, 0, null);
                leftImage.recycle();

                canvas.drawBitmap(scaledTextImage, 640, 0, null);
                scaledTextImage.recycle();
                textImage.recycle();
                return resultBitmap;
            }
        }
    } catch (Exception e) {}
    finally { try { if (is != null) is.close(); } catch (Exception e) {} }
    return null;
}

void downloadImage(String imageUrl, String localPath) {
    InputStream inputStream = null;
    FileOutputStream outputStream = null;
    try {
        if (imageUrl.startsWith("http://")) imageUrl = imageUrl.replaceFirst("http://", "https://");
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.connect();
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK || connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(localPath);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
        }
    } catch (Exception e) {}
    finally {
        try { if (inputStream != null) inputStream.close(); } catch (Exception e) {}
        try { if (outputStream != null) outputStream.close(); } catch (Exception e) {}
    }
}

Typeface getTypefaceFromFile(String fontName) {
    String fontPath = pluginPath + "/把字体放在这个文件夹里/" + fontName + ".ttf";
    return getTypefaceFromPath(fontPath);
}

Typeface getTypefaceFromPath(String fontPath) {
    if (fontPath == null) return Typeface.DEFAULT;
    File fontFile = new File(fontPath);
    if (fontFile.exists()) {
        try { return Typeface.createFromFile(fontFile); } catch (Exception e) {}
    }
    return Typeface.DEFAULT;
}

StaticLayout createTextLayout(CharSequence text, TextPaint paint, int width) {
    return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
}

void saveBitmapToFile(Bitmap bitmap, String filePath) {
    FileOutputStream fos = null;
    try {
        fos = new FileOutputStream(filePath);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
    } catch (Exception e) {}
    finally { try { if (fos != null) fos.close(); } catch (Exception e) {} }
}

RecyclerView findChatRecyclerView(Activity act) {
    View decor = act.getWindow().getDecorView();
    String targetClass = "com.tencent.aio.part.root.panel.content.firstLevel.msglist.mvx.vb.ui.adapter.a";
    return findRecyclerViewByAdapterClass(decor, targetClass);
}

RecyclerView findRecyclerViewByAdapterClass(View view, String adapterClassName) {
    if (view == null) return null;
    if (view instanceof RecyclerView) {
        RecyclerView rv = (RecyclerView) view;
        RecyclerView.Adapter adapter = rv.getAdapter();
        if (adapter != null && adapter.getClass().getName().equals(adapterClassName)) return rv;
    }
    if (view instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            RecyclerView result = findRecyclerViewByAdapterClass(group.getChildAt(i), adapterClassName);
            if (result != null) return result;
        }
    }
    return null;
}