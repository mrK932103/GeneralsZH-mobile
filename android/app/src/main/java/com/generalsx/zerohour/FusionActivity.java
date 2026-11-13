/*
**  Command & Conquer Generals Zero Hour(tm)
**  Copyright 2025 Electronic Arts Inc.
**
**  This program is free software: you can redistribute it and/or modify
**  it under the terms of the GNU General Public License as published by
**  the Free Software Foundation, either version 3 of the License, or
**  (at your option) any later version.
*/

package com.generalsx.zerohour;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * First user-facing Generals Mod Fusion workflow.
 *
 * Lets the player choose any installed mods and runs the existing N-way BIGF/INI
 * preflight on a worker thread. It deliberately does not emit a playable package:
 * semantic merge rules and engine-level validation are not implemented yet.
 */
public final class FusionActivity extends Activity {
    private final List<ModManager.Mod> mods = new ArrayList<>();
    private final List<CheckBox> choices = new ArrayList<>();
    private LinearLayout root;
    private TextView report;
    private Button analyze;
    private boolean running;

    @Override
    protected void onCreate(Bundle state) {
        ThemeHelper.apply(this);
        super.onCreate(state);
        ThemeHelper.applyBars(this);
        setTitle("Generals Mod Fusion");

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(247, 247, 249));
        scroll.addView(root);
        setContentView(scroll);
        InsetUtil.applySafeInsets(root);

        TextView heading = text("Generals Mod Fusion", 24, true);
        root.addView(heading);
        root.addView(text("Выбери несколько установленных модов. Fusion проверит BIG-архивы, INI-определения и пересечения ресурсов.", 15, false));
        root.addView(text("Поддерживается анализ любого количества установленных модов. Совместимость конкретных модов определяется содержимым файлов, а не названием.", 13, false));

        List<ModManager.Mod> found = ModManager.listMods(this);
        mods.addAll(found);
        root.addView(text("Установленные моды", 18, true));
        if (mods.isEmpty()) {
            root.addView(text("Моды не найдены. Сначала импортируй папки или BIG/ZIP через экран «Моды».", 14, false));
        }
        for (ModManager.Mod mod : mods) {
            CheckBox check = new CheckBox(this);
            check.setText(mod.name + (mod.version.isEmpty() ? "" : "  •  " + mod.version));
            check.setTextSize(15);
            check.setTextColor(Color.rgb(30, 30, 34));
            check.setChecked(mod.enabled);
            choices.add(check);
            root.addView(check);
        }

        analyze = new Button(this);
        analyze.setText("Проверить выбранные моды");
        analyze.setAllCaps(false);
        analyze.setOnClickListener(v -> runAnalysis());
        root.addView(analyze);

        report = text("Результат проверки появится здесь.", 14, false);
        report.setTextIsSelectable(true);
        root.addView(report);
    }

    private void runAnalysis() {
        if (running) return;
        List<ModManager.Mod> selected = new ArrayList<>();
        for (int i = 0; i < mods.size(); i++) {
            if (choices.get(i).isChecked()) selected.add(mods.get(i));
        }
        running = true;
        analyze.setEnabled(false);
        report.setText("Проверяю архивы и INI-файлы…");
        new Thread(() -> {
            String output;
            try {
                FusionPreflight.Result result = FusionPreflight.inspect(selected);
                FusionMergePlan.Result plan = result.assessMergePlan();
                StringBuilder out = new StringBuilder();
                out.append("ПРОВЕРКА FUSION\n");
                out.append("Выбрано модов: ").append(selected.size()).append("\n");
                out.append("BIG-архивов: ").append(result.archiveCount).append("\n");
                out.append("Записей в архивах: ").append(result.entryCount).append("\n");
                out.append("INI-определений: ").append(result.definitionCount()).append("\n");
                out.append("INI-ссылок: ").append(result.referenceCount()).append("\n");
                out.append("Дублирующихся путей: ").append(result.duplicatePathCount()).append("\n");
                out.append("Конфликтов определений: ").append(result.duplicateDefinitionCount()).append("\n\n");
                out.append("ПРОБЛЕМЫ ПРЕДВАРИТЕЛЬНОЙ ПРОВЕРКИ\n");
                if (result.issues.isEmpty()) {
                    out.append("Критических проблем инвентаризации не найдено.\n");
                } else {
                    for (FusionPreflight.Issue issue : result.issues) {
                        out.append(issue.blocking ? "[БЛОКИРУЕТ] " : "[ПРЕДУПРЕЖДЕНИЕ] ")
                            .append(issue.message).append("\n");
                    }
                }
                out.append("\nПЛАН ОБЪЕДИНЕНИЯ\n");
                out.append(plan.summary(35)).append("\n");
                out.append("\n");
                out.append("Создание игрового Fusion-пакета: ПОКА НЕДОСТУПНО.\n");
                out.append("Эта проверка ничего не изменяет в исходных модах и не создаёт архив, который можно запускать в игре.");
                output = out.toString();
            } catch (Throwable error) {
                output = "Ошибка анализа: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage());
            }
            final String finalOutput = output;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                report.setText(finalOutput);
                running = false;
                analyze.setEnabled(true);
            });
        }, "GeneralsX-Fusion-Preflight").start();
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 33, 36));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(7), 0, dp(7));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
