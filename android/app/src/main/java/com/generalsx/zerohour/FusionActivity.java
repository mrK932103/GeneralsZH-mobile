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
import java.io.File;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
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
    private Button build;
    private List<ModManager.Mod> selectedForBuild = new ArrayList<>();
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

        build = new Button(this);
        build.setText("Сборка изолированных фракций ещё в разработке");
        build.setAllCaps(false);
        build.setEnabled(false);
        build.setOnClickListener(v -> buildPackage());
        root.addView(build);

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
            boolean canBuild = false;
            try {
                FusionPreflight.Result result = FusionPreflight.inspect(selected);
                canBuild = !selected.isEmpty() && result.canBuildArchiveOverlay()
                    && result.duplicateDefinitionCount() == 0;
                FusionMergePlan.Result plan = result.assessMergePlan();
                FusionNamespacePlan.Result definitionNamespaces =
                    result.assessDefinitionNamespaces();
                FusionAssetNamespacePlan.Result assetNamespaces =
                    result.assessAssetNamespaces();
                StringBuilder out = new StringBuilder();
                out.append("ПРОВЕРКА FUSION\n");
                out.append("Выбрано модов: ").append(selected.size()).append("\n");
                out.append("BIG-архивов: ").append(result.archiveCount).append("\n");
                out.append("Записей в архивах: ").append(result.entryCount).append("\n");
                out.append("INI-определений: ").append(result.definitionCount()).append("\n");
                out.append("INI-ссылок: ").append(result.referenceCount()).append("\n");
                List<FusionFactionCatalog.FactionSource> factions =
                    FusionFactionCatalog.discover(result.iniBlocks);
                out.append("Найдено PlayerTemplate-фракций/кандидатов: ")
                    .append(factions.size()).append("\n");
                for (FusionFactionCatalog.FactionSource faction : factions) {
                    out.append("  - ").append(faction.describe()).append("\n");
                    for (String unresolved : faction.unresolved) {
                        out.append("      REVIEW: ").append(unresolved).append("\n");
                    }
                }
                out.append("Дублирующихся путей: ").append(result.duplicatePathCount()).append("\n");
                out.append("Конфликтов определений: ").append(result.duplicateDefinitionCount()).append("\n");
                out.append("План уникальных имён INI: ").append(definitionNamespaces.entries.size()).append("\n");
                out.append("Не разрешено INI-ссылок: ").append(definitionNamespaces.unresolved.size()).append("\n");
                out.append("План переименования путей ресурсов: ").append(assetNamespaces.remappedCount()).append("\n");
                out.append("Проблем изоляции путей ресурсов: ").append(assetNamespaces.unresolved.size()).append("\n\n");
                if (!definitionNamespaces.unresolved.isEmpty()) {
                    out.append("ПРОВЕРИТЬ INI-ССЫЛКИ (первые 10)\n");
                    int shown = 0;
                    for (String unresolved : definitionNamespaces.unresolved) {
                        if (shown++ >= 10) break;
                        out.append("  - ").append(unresolved).append("\n");
                    }
                }
                if (!assetNamespaces.unresolved.isEmpty()) {
                    out.append("ПРОВЕРИТЬ ПУТИ РЕСУРСОВ (первые 10)\n");
                    int shown = 0;
                    for (String unresolved : assetNamespaces.unresolved) {
                        if (shown++ >= 10) break;
                        out.append("  - ").append(unresolved).append("\n");
                    }
                }
                out.append("\n");
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
                out.append("Безопасная сборка BIG-пакета: ").append(canBuild ? "ДОСТУПНА ПОСЛЕ ПРОВЕРКИ" : "ЗАБЛОКИРОВАНА").append("\n");
                out.append("ВАЖНО: найденные PlayerTemplate — только корни фракций. Их зависимости, ресурсы и изоляция в движке ещё не подключены. Архив-перекрытие не создаёт совместимые фракции. Исходные моды не изменяются.");
                output = out.toString();
            } catch (Throwable error) {
                output = "Ошибка анализа: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage());
            }
            final String finalOutput = output;
            final boolean buildAllowed = canBuild;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                report.setText(finalOutput);
                selectedForBuild = new ArrayList<>(selected);
                build.setEnabled(false);
                running = false;
                analyze.setEnabled(true);
            });
        }, "GeneralsX-Fusion-Preflight").start();
    }

    /**
     * Build only a strict archive overlay: no duplicate INI definitions, no unsupported
     * loose assets, and no differing content for the same virtual path. This is not
     * advertised as a playable faction merge; the game must still be tested.
     */
    private void buildPackage() {
        if (running || selectedForBuild.isEmpty()) return;
        File rootDir = ModManager.modsRoot(this);
        if (rootDir == null || (!rootDir.isDirectory() && !rootDir.mkdirs())) {
            report.setText("Не удалось открыть папку mods игры.");
            return;
        }
        File packageDir = new File(rootDir, "GeneralsModFusion");
        for (int suffix = 2; packageDir.exists() && suffix < 1000; suffix++) {
            packageDir = new File(rootDir, "GeneralsModFusion-" + suffix);
        }
        if (packageDir.exists() || !packageDir.mkdirs()) {
            report.setText("Не удалось создать новую папку Fusion. Исходные моды не изменены.");
            return;
        }
        final File output = new File(packageDir, "GeneralsModFusion.big");
        final List<ModManager.Mod> selection = new ArrayList<>(selectedForBuild);
        running = true;
        analyze.setEnabled(false);
        build.setEnabled(false);
        report.setText("Создаю BIG-пакет. Исходные моды не изменяются…");
        new Thread(() -> {
            String message;
            boolean success = false;
            try {
                FusionArchiveBuild.Result result = FusionArchiveBuild.build(selection, output);
                message = "BIG-пакет создан.\n"
                    + "Модов-источников: " + result.sourceMods + "\n"
                    + "Исходных архивов: " + result.sourceArchives + "\n"
                    + "Записей в новом архиве: " + result.entries + "\n"
                    + "Файл: " + result.archive.getAbsolutePath() + "\n\n"
                    + "Вернись в список модов: новый пакет появится там выключенным. "
                    + "Для проверки включи Fusion-пакет и выключи исходные моды. "
                    + "Это безопасная сборка непересекающихся файлов, а не гарантия "
                    + "совместной игры фракций; обязательно проверь запуск и Skirmish.";
                success = true;
            } catch (Throwable error) {
                message = "Сборка остановлена: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage())
                    + "\nИсходные моды не изменялись.";
                // Remove only the empty/new output folder created by this operation.
                if (output.exists()) output.delete();
                packageDir.delete();
            }
            final String finalMessage = message;
            final boolean built = success;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                report.setText(finalMessage);
                running = false;
                analyze.setEnabled(true);
                build.setEnabled(false);
                if (built) {
                    Toast.makeText(this, "Fusion BIG-пакет создан", Toast.LENGTH_LONG).show();
                }
            });
        }, "GeneralsX-Fusion-Build").start();
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
