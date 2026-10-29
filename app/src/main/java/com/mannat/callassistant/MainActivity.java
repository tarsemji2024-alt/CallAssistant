package com.mannat.callassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

public class MainActivity extends Activity {
    static final int REQ_FILE = 1, REQ_CALL = 2;
    static final String DEFAULT_SCRIPT =
            "नमस्ते, मैं ___ से बोल रहा हूँ।\n"
            + "हमारे पास हाई-स्पीड WiFi प्लान हैं:\n"
            + "• प्लान 1: __ Mbps – ₹__ /माह\n"
            + "• प्लान 2: __ Mbps – ₹__ /माह\n"
            + "• इंस्टॉलेशन: __\n"
            + "क्या आप कनेक्शन लगवाना चाहेंगे?";

    SharedPreferences sp;
    JSONArray list = new JSONArray();
    String script;
    int cur = -1;
    String pendingCall;
    TextView tvStats, tvName, tvNumber, tvStatus, tvScript;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("ca", MODE_PRIVATE);
        try { list = new JSONArray(sp.getString("list", "[]")); } catch (Exception e) { list = new JSONArray(); }
        script = sp.getString("script", DEFAULT_SCRIPT);
        buildUi();
        refresh();
    }

    // ---------- UI ----------
    void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        sv.addView(root);
        setContentView(sv);

        root.addView(tv("📞 कॉल असिस्टेंट", 22, true));
        tvStats = tv("", 14, false);
        root.addView(tvStats);

        LinearLayout r1 = row();
        r1.addView(btn("📂 फ़ाइल से लिस्ट", v -> pickFile()), w());
        r1.addView(btn("📋 पेस्ट करें", v -> pasteDialog()), w());
        root.addView(r1);

        LinearLayout r2 = row();
        r2.addView(btn("📝 स्क्रिप्ट", v -> scriptDialog()), w());
        r2.addView(btn("📤 तैयार कस्टमर शेयर", v -> exportData()), w());
        r2.addView(btn("🗑 साफ़", v -> clearAll()), w());
        root.addView(r2);

        tvName = tv("", 26, true);
        tvName.setPadding(0, dp(18), 0, 0);
        root.addView(tvName);
        tvNumber = tv("", 20, false);
        root.addView(tvNumber);
        tvStatus = tv("", 13, false);
        root.addView(tvStatus);

        tvScript = tv("", 16, false);
        tvScript.setBackgroundColor(0xFFF1F1F1);
        tvScript.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp1.setMargins(0, dp(10), 0, dp(10));
        root.addView(tvScript, sp1);

        LinearLayout r3 = row();
        r3.addView(btn("📞 कॉल करें", v -> callNow()), w());
        r3.addView(btn("💬 WhatsApp", v -> whatsapp()), w());
        root.addView(r3);

        LinearLayout r4 = row();
        r4.addView(btn("✅ तैयार है", v -> interestedDialog()), w());
        r4.addView(btn("❌ नहीं चाहिए", v -> mark("no")), w());
        root.addView(r4);

        LinearLayout r5 = row();
        r5.addView(btn("🕐 बाद में", v -> mark("later")), w());
        r5.addView(btn("📵 नहीं उठाया", v -> mark("noanswer")), w());
        root.addView(r5);
    }

    TextView tv(String s, int sz, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sz);
        t.setTextColor(0xFF111111);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    Button btn(String s, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    LinearLayout.LayoutParams w() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density); }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- Data ----------
    JSONObject get(int i) {
        try { return list.getJSONObject(i); } catch (Exception e) { return new JSONObject(); }
    }

    void save() { sp.edit().putString("list", list.toString()).apply(); }

    int find(String st) {
        for (int i = 0; i < list.length(); i++)
            if (st.equals(get(i).optString("status"))) return i;
        return -1;
    }

    int findCurrent() {
        int i = find("pending");
        if (i < 0) i = find("later");
        if (i < 0) i = find("noanswer");
        return i;
    }

    void refresh() {
        cur = findCurrent();
        int pend = 0, intr = 0;
        for (int i = 0; i < list.length(); i++) {
            String s = get(i).optString("status");
            if (s.equals("pending")) pend++;
            if (s.equals("interested")) intr++;
        }
        tvStats.setText("कुल: " + list.length() + "   |   बाकी: " + pend + "   |   तैयार: " + intr);
        tvScript.setText(script);
        if (cur < 0) {
            tvName.setText(list.length() == 0 ? "लिस्ट खाली है" : "सब कस्टमर हो गए 🎉");
            tvNumber.setText(list.length() == 0 ? "ऊपर से फ़ाइल या पेस्ट करके लिस्ट जोड़ें" : "");
            tvStatus.setText("");
        } else {
            JSONObject o = get(cur);
            tvName.setText(o.optString("name"));
            tvNumber.setText(o.optString("number"));
            String st = o.optString("status");
            tvStatus.setText(st.equals("later") ? "(बाद में कॉल वाला)" : st.equals("noanswer") ? "(पिछली बार फ़ोन नहीं उठा)" : "");
        }
    }

    void mark(String st) {
        if (cur < 0) return;
        try { list.getJSONObject(cur).put("status", st); } catch (Exception e) { }
        save();
        refresh();
    }

    static String digits(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray())
            if (Character.isDigit(c) || (c == '+' && b.length() == 0)) b.append(c);
        return b.toString();
    }

    int addFromText(String text) {
        int added = 0;
        HashSet<String> have = new HashSet<>();
        for (int i = 0; i < list.length(); i++) have.add(digits(get(i).optString("number")));
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String name = "", num = "";
            for (String x : line.split("[,;\\t]")) {
                x = x.trim().replace("\"", "");
                String d = digits(x);
                if (num.isEmpty() && d.replace("+", "").length() >= 10) num = d;
                else if (name.isEmpty()) name = x;
            }
            if (num.isEmpty() || !have.add(num)) continue;
            if (name.isEmpty()) name = num;
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("number", num);
                o.put("status", "pending");
                list.put(o);
                added++;
            } catch (Exception e) { }
        }
        save();
        refresh();
        return added;
    }

    // ---------- Actions ----------
    void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "CSV / TXT फ़ाइल चुनें"), REQ_FILE);
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData());
                 ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                String t = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                if (t.startsWith("\uFEFF")) t = t.substring(1);
                toast(addFromText(t) + " नए नंबर जुड़े");
            } catch (Exception e) {
                toast("फ़ाइल नहीं पढ़ पाया");
            }
        }
    }

    void pasteDialog() {
        final EditText e = new EditText(this);
        e.setHint("नाम,नंबर  (हर लाइन में एक)");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(6);
        e.setGravity(android.view.Gravity.TOP);
        new AlertDialog.Builder(this).setTitle("लिस्ट पेस्ट करें").setView(pad(e))
                .setPositiveButton("जोड़ें", (d, x) -> toast(addFromText(e.getText().toString()) + " नए नंबर जुड़े"))
                .setNegativeButton("रद्द", null).show();
    }

    void scriptDialog() {
        final EditText e = new EditText(this);
        e.setText(script);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(8);
        e.setGravity(android.view.Gravity.TOP);
        new AlertDialog.Builder(this).setTitle("प्लान की स्क्रिप्ट").setView(pad(e))
                .setPositiveButton("सेव", (d, x) -> {
                    script = e.getText().toString();
                    sp.edit().putString("script", script).apply();
                    refresh();
                })
                .setNegativeButton("रद्द", null).show();
    }

    LinearLayout pad(android.view.View v) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(dp(20), dp(10), dp(20), 0);
        l.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    EditText et(String hint, String val, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        return e;
    }

    void interestedDialog() {
        if (cur < 0) return;
        final JSONObject o = get(cur);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(10), dp(20), 0);
        final EditText name = et("नाम", o.optString("name"), false);
        final EditText addr = et("पता", o.optString("address"), true);
        final EditText plan = et("कौन सा प्लान", o.optString("plan"), false);
        final EditText note = et("नोट", o.optString("note"), true);
        l.addView(name);
        l.addView(addr);
        l.addView(plan);
        l.addView(note);
        new AlertDialog.Builder(this).setTitle("कस्टमर तैयार है").setView(l)
                .setPositiveButton("सेव", (d, x) -> {
                    try {
                        o.put("name", name.getText().toString());
                        o.put("address", addr.getText().toString());
                        o.put("plan", plan.getText().toString());
                        o.put("note", note.getText().toString());
                        o.put("status", "interested");
                    } catch (Exception ex) { }
                    save();
                    refresh();
                    toast("सेव हो गया ✅");
                })
                .setNegativeButton("रद्द", null).show();
    }

    void callNow() {
        if (cur < 0) return;
        String num = get(cur).optString("number");
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCall = num;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }
        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + num)));
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (rc == REQ_CALL && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pendingCall != null) {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + pendingCall)));
        }
        pendingCall = null;
    }

    void whatsapp() {
        if (cur < 0) return;
        String n = digits(get(cur).optString("number")).replace("+", "");
        if (n.length() == 10) n = "91" + n;
        String url = "https://wa.me/" + n + "?text=" + Uri.encode(script);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    static String q(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    void exportData() {
        StringBuilder sb = new StringBuilder("Name,Number,Address,Plan,Note\n");
        int c = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = get(i);
            if (!"interested".equals(o.optString("status"))) continue;
            sb.append(q(o.optString("name"))).append(',')
              .append(q(o.optString("number"))).append(',')
              .append(q(o.optString("address"))).append(',')
              .append(q(o.optString("plan"))).append(',')
              .append(q(o.optString("note"))).append('\n');
            c++;
        }
        if (c == 0) { toast("अभी कोई तैयार कस्टमर नहीं है"); return; }
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("text/plain");
        s.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(s, "शेयर करें"));
    }

    void clearAll() {
        new AlertDialog.Builder(this).setTitle("पूरी लिस्ट साफ़ करें?")
                .setMessage("सारे कस्टमर और उनका डेटा मिट जाएगा। पहले 'तैयार कस्टमर शेयर' से डेटा बचा लें।")
                .setPositiveButton("साफ़ करें", (d, x) -> { list = new JSONArray(); save(); refresh(); })
                .setNegativeButton("रद्द", null).show();
    }
}
