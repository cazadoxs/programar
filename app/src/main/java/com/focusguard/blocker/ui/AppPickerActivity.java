package com.focusguard.blocker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Platform;
import com.focusguard.blocker.data.Prefs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lists every launchable app on the phone so the user can choose which ones FocusGuard protects. */
public class AppPickerActivity extends Activity {

    private static final class Entry {
        final String pkg;
        final String label;
        final Drawable icon;

        Entry(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private Prefs prefs;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setTitle(R.string.custom_picker_title);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        EditText search = new EditText(this);
        search.setHint(R.string.custom_search_hint);
        search.setSingleLine(true);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                LinearLayout row;
                if (convert instanceof LinearLayout) {
                    row = (LinearLayout) convert;
                } else {
                    row = new LinearLayout(AppPickerActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, pad / 2, 0, pad / 2);
                    ImageView icon = new ImageView(AppPickerActivity.this);
                    row.addView(icon, new LinearLayout.LayoutParams(pad * 5 / 2, pad * 5 / 2));
                    TextView label = new TextView(AppPickerActivity.this);
                    label.setTextSize(16);
                    label.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.leftMargin = pad;
                    row.addView(label, lp);
                }
                Entry e = shown.get(i);
                ((ImageView) row.getChildAt(0)).setImageDrawable(e.icon);
                ((TextView) row.getChildAt(1)).setText(e.label);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> pickMode(shown.get(pos)));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        loadApps();
    }

    private int getColorFromAttr(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return getColor(tv.resourceId);
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<Entry> loaded = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                if (Platform.forPackage(pkg) != null) continue;      // built-in rows already exist
                if (prefs.getGuardMode(pkg) != 0) continue;          // already guarded
                loaded.add(new Entry(pkg, String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
            }
            final Collator collator = Collator.getInstance();
            Collections.sort(loaded, (a, b) -> collator.compare(a.label, b.label));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(loaded);
                filter("");
            });
        }).start();
    }

    private void filter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Entry e : all) {
            if (q.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(e);
        }
        adapter.notifyDataSetChanged();
    }

    private void pickMode(Entry entry) {
        String[] items = {getString(R.string.custom_mode_feed), getString(R.string.custom_mode_whole)};
        new AlertDialog.Builder(this)
                .setTitle(entry.label + " — " + getString(R.string.custom_pick_mode))
                .setItems(items, (d, which) -> {
                    prefs.setGuard(entry.pkg, which == 0 ? Prefs.MODE_FEED : Prefs.MODE_WHOLE);
                    finish();
                })
                .show();
    }
}
