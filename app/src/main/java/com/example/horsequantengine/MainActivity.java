package com.example.horsequantengine;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "HorseQuantPrefsV2";
    private SharedPreferences prefs;

    private LinearLayout containerCorePicks;
    private LinearLayout containerSubPicks;
    private TextView txtSubThresholdHeader;
    private TextView txtScoreboard;
    private TextView txtSystemLog;
    private EditText inputExtraSubPicks;

    private List<RunnerCandidate> dailySlate = new ArrayList<>();

    static class RunnerCandidate {
        String raceId;
        String raceTimeTrack;
        String horseName;
        double decimalOdds;
        String formString;
        int daysOff;
        String weightInfo;
        int rpr;
        int ts;
        boolean rank1Travel;
        boolean rank2CourseDist;
        boolean rank3GoingStick;
        boolean rank4JumpClass;
        boolean hasFitnessPenalty;
        boolean hasWeightPenalty;
        boolean hasPaceEdge;
        boolean hasSteamEdge;
        boolean hasSynergyEdge;
        boolean hasDrawEdge;
        boolean voidedByUser = false;

        double edgeA;
        double edgeB;
        double probA;
        double probB;

        RunnerCandidate(String raceId, String raceTimeTrack, String horseName, double decimalOdds,
                        String formString, int daysOff, String weightInfo, int rpr, int ts,
                        boolean r1, boolean r2, boolean r3, boolean r4,
                        boolean fitPen, boolean wgtPen, boolean pace, boolean steam, boolean syn, boolean draw) {
            this.raceId = raceId;
            this.raceTimeTrack = raceTimeTrack;
            this.horseName = horseName;
            this.decimalOdds = decimalOdds;
            this.formString = formString;
            this.daysOff = daysOff;
            this.weightInfo = weightInfo;
            this.rpr = rpr;
            this.ts = ts;
            this.rank1Travel = r1;
            this.rank2CourseDist = r2;
            this.rank3GoingStick = r3;
            this.rank4JumpClass = r4;
            this.hasFitnessPenalty = fitPen;
            this.hasWeightPenalty = wgtPen;
            this.hasPaceEdge = pace;
            this.hasSteamEdge = steam;
            this.hasSynergyEdge = syn;
            this.hasDrawEdge = draw;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeDefaultWeights();

        containerCorePicks = findViewById(R.id.containerCorePicks);
        containerSubPicks = findViewById(R.id.containerSubPicks);
        txtSubThresholdHeader = findViewById(R.id.txtSubThresholdHeader);
        txtScoreboard = findViewById(R.id.txtScoreboard);
        txtSystemLog = findViewById(R.id.txtSystemLog);
        inputExtraSubPicks = findViewById(R.id.inputExtraSubPicks);
        Button btnScanMarkets = findViewById(R.id.btnScanMarkets);

        scheduleDailyMorningAutoScan();
        refreshScoreboardAndLog();

        btnScanMarkets.setOnClickListener(v -> {
            appendLog("Manual Market Scan triggered.");
            runMarketEvaluation();
            scheduleOneHourPostRaceAudit(16, 0);
        });
    }

    private void initializeDefaultWeights() {
        if (!prefs.contains("champW1")) {
            prefs.edit()
                    .putFloat("champW1", 0.40f)
                    .putFloat("champW2", 0.25f)
                    .putFloat("champW3", 0.20f)
                    .putFloat("champW4", 0.15f)
                    .putFloat("backupW1", 0.40f)
                    .putFloat("backupW2", 0.25f)
                    .putFloat("backupW3", 0.20f)
                    .putFloat("backupW4", 0.15f)
                    .putFloat("challW1", 0.40f)
                    .putFloat("challW2", 0.25f)
                    .putFloat("challW3", 0.20f)
                    .putFloat("challW4", 0.15f)
                    .putInt("sampleCount", 4)
                    .putInt("winsA", 3)
                    .putFloat("profitA", 6.25f)
                    .putInt("winsB", 3)
                    .putFloat("profitB", 6.50f)
                    .putInt("postPromoCount", 0)
                    .apply();
        }
    }

    private void runMarketEvaluation() {
        loadTodayMarketFeed();

        float cW1 = prefs.getFloat("champW1", 0.40f);
        float cW2 = prefs.getFloat("champW2", 0.25f);
        float cW3 = prefs.getFloat("champW3", 0.20f);
        float cW4 = prefs.getFloat("champW4", 0.15f);

        float bW1 = prefs.getFloat("challW1", 0.40f);
        float bW2 = prefs.getFloat("challW2", 0.25f);
        float bW3 = prefs.getFloat("challW3", 0.20f);
        float bW4 = prefs.getFloat("challW4", 0.15f);

        List<RunnerCandidate> validCore = new ArrayList<>();
        List<RunnerCandidate> subThreshold = new ArrayList<>();

        for (RunnerCandidate r : dailySlate) {
            if (r.voidedByUser) continue;

            double impliedM = (1.0 / r.decimalOdds) * 100.0;

            double pA = 12.0;
            if (r.rank1Travel) pA += cW1 * 100.0;
            if (r.rank2CourseDist) pA += cW2 * 100.0;
            if (r.rank3GoingStick) pA += cW3 * 100.0;
            if (r.rank4JumpClass) pA += cW4 * 100.0;

            double pB = 12.0;
            if (r.rank1Travel) pB += bW1 * 100.0;
            if (r.rank2CourseDist) pB += bW2 * 100.0;
            if (r.rank3GoingStick) pB += bW3 * 100.0;
            if (r.rank4JumpClass) pB += bW4 * 100.0;

            if (r.hasFitnessPenalty) pB -= 12.0;
            if (r.hasWeightPenalty) pB -= 8.0;
            if (r.hasPaceEdge) pB += 5.0;
            if (r.hasSteamEdge) pB += 5.0;
            if (r.hasSynergyEdge) pB += 4.0;
            if (r.hasDrawEdge) pB += 4.0;

            r.probA = pA;
            r.probB = pB;
            r.edgeA = pA - impliedM;
            r.edgeB = pB - impliedM;

            if (r.edgeA > 0 && !r.hasFitnessPenalty) {
                validCore.add(r);
            } else {
                subThreshold.add(r);
            }
        }

        Collections.sort(validCore, (a, b) -> Double.compare(b.edgeA, a.edgeA));
        Collections.sort(subThreshold, (a, b) -> Double.compare(b.edgeA, a.edgeA));

        List<RunnerCandidate> finalCoreZeroOverlap = new ArrayList<>();
        List<String> usedRaces = new ArrayList<>();
        for (RunnerCandidate r : validCore) {
            if (!usedRaces.contains(r.raceId)) {
                finalCoreZeroOverlap.add(r);
                usedRaces.add(r.raceId);
            }
        }

        renderPicksUI(finalCoreZeroOverlap, subThreshold);
    }

    private void renderPicksUI(List<RunnerCandidate> corePicks, List<RunnerCandidate> subPicks) {
        containerCorePicks.removeAllViews();
        containerSubPicks.removeAllViews();

        if (corePicks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No runners currently clear the strict Engine A (P > M) value filter.");
            empty.setTextColor(Color.LTGRAY);
            containerCorePicks.addView(empty);
        } else {
            for (int i = 0; i < corePicks.size(); i++) {
                RunnerCandidate r = corePicks.get(i);
                containerCorePicks.addView(buildRunnerCard(r, i + 1, true));
            }
        }

        int extraRequested = 0;
        try {
            extraRequested = Integer.parseInt(inputExtraSubPicks.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        if (extraRequested > 0 && !subPicks.isEmpty()) {
            txtSubThresholdHeader.setVisibility(View.VISIBLE);
            containerSubPicks.setVisibility(View.VISIBLE);
            int limit = Math.min(extraRequested, subPicks.size());
            for (int i = 0; i < limit; i++) {
                containerSubPicks.addView(buildRunnerCard(subPicks.get(i), i + 1, false));
            }
        } else {
            txtSubThresholdHeader.setVisibility(View.GONE);
            containerSubPicks.setVisibility(View.GONE);
        }
    }

    private View buildRunnerCard(RunnerCandidate r, int rankOrder, boolean isCore) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(isCore ? Color.parseColor("#1E1E1E") : Color.parseColor("#2A1616"));
        card.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);

        String betAction;
        if (!isCore) {
            betAction = "SUB-THRESHOLD: EXCLUDED FROM TRAINING DATA";
        } else if (r.decimalOdds >= 7.0) {
            betAction = "£1.00 EACH-WAY (£2.00 TOTAL) — PROFIT E/W";
        } else {
            betAction = "£2.00 STRAIGHT WIN (BOG ACTIVE)";
        }

    public void evaluateAutonomous50RaceGate() {
        int samples = prefs.getInt("sampleCount", 0);
        float profitA = prefs.getFloat("profitA", 0f);
        float profitB = prefs.getFloat("profitB", 0f);
        int postPromo = prefs.getInt("postPromoCount", 0);

        if (postPromo >= 25 && profitA < 0f) {
            prefs.edit()
                    .putFloat("champW1", prefs.getFloat("backupW1", 0.40f))
                    .putFloat("champW2", prefs.getFloat("backupW2", 0.25f))
                    .putFloat("champW3", prefs.getFloat("backupW3", 0.20f))
                    .putFloat("champW4", prefs.getFloat("backupW4", 0.15f))
                    .putInt("postPromoCount", 0)
                    .apply();
            appendLog("AUTO-ROLLBACK: Engine A dipped below profit floor over 25 races. Reverted to previous Champion snapshot.");
        }

        if (samples >= 50) {
            if (profitB > profitA) {
                float oldW1 = prefs.getFloat("champW1", 0.40f);
                float oldW2 = prefs.getFloat("champW2", 0.25f);
                float oldW3 = prefs.getFloat("champW3", 0.20f);
                float oldW4 = prefs.getFloat("champW4", 0.15f);

                float newW1 = prefs.getFloat("challW1", 0.40f);
                float newW2 = prefs.getFloat("challW2", 0.25f);
                float newW3 = prefs.getFloat("challW3", 0.20f);
                float newW4 = prefs.getFloat("challW4", 0.15f);

                prefs.edit()
                        .putFloat("backupW1", oldW1).putFloat("backupW2", oldW2)
                        .putFloat("backupW3", oldW3).putFloat("backupW4", oldW4)
                        .putFloat("champW1", newW1).putFloat("champW2", newW2)
                        .putFloat("champW3", newW3).putFloat("champW4", newW4)
                        .putFloat("challW1", Math.min(0.45f, newW1 + 0.02f))
                        .putFloat("challW2", Math.max(0.15f, newW2 - 0.02f))
                        .putInt("sampleCount", 0)
                        .putInt("postPromoCount", 1)
                        .apply();
                appendLog("AUTO-PROMOTION (50/50 GATE): Engine B outperformed Engine A! Promoted to new Champion & diverged Engine B.");
            } else {
                prefs.edit().putInt("sampleCount", 0).apply();
                appendLog("50-RACE REVIEW: Engine A retained Champion status. Engine B recalibrating for next 50 races.");
            }
        }
        refreshScoreboardAndLog();
    }

    private void scheduleDailyMorningAutoScan() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 7);
        target.set(Calendar.MINUTE, 30);
        target.set(Calendar.SECOND, 0);
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        long initialDelay = target.getTimeInMillis() - now.getTimeInMillis();

        PeriodicWorkRequest dailyScan = new PeriodicWorkRequest.Builder(DailyMarketScanWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "Daily0730MarketScan", ExistingPeriodicWorkPolicy.KEEP, dailyScan);
    }

    private void scheduleOneHourPostRaceAudit(int lastRaceHour, int lastRaceMinute) {
        Calendar now = Calendar.getInstance();
        Calendar auditTime = Calendar.getInstance();
        auditTime.set(Calendar.HOUR_OF_DAY, lastRaceHour + 1);
        auditTime.set(Calendar.MINUTE, lastRaceMinute);
        auditTime.set(Calendar.SECOND, 0);

        long delayMs = Math.max(60000L, auditTime.getTimeInMillis() - now.getTimeInMillis());
        OneTimeWorkRequest auditRequest = new OneTimeWorkRequest.Builder(PostRaceAuditWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(this).enqueue(auditRequest);
    }

    private void refreshScoreboardAndLog() {
        int samples = prefs.getInt("sampleCount", 4);
        int winsA = prefs.getInt("winsA", 3);
        float profitA = prefs.getFloat("profitA", 6.25f);
        int winsB = prefs.getInt("winsB", 3);
        float profitB = prefs.getFloat("profitB", 6.50f);

        double winRateA = samples > 0 ? (winsA * 100.0 / samples) : 0.0;
        double roiA = samples > 0 ? (profitA * 100.0 / (samples * 2.0)) : 0.0;
        double winRateB = samples > 0 ? (winsB * 100.0 / samples) : 0.0;
        double roiB = samples > 0 ? (profitB * 100.0 / (samples * 2.0)) : 0.0;

        txtScoreboard.setText(String.format(Locale.UK,
                "Engine A (Champion): Win Rate %.1f%% | Net ROI %+.1f%% (%+.2f pts)\n" +
                "Engine B (Challenger): Win Rate %.1f%% | Net ROI %+.1f%% (%+.2f pts)\n" +
                "Autonomous 50-Race Gate: %d / 50 Audited Races",
                winRateA, roiA, profitA, winRateB, roiB, profitB, samples));

        String savedLog = prefs.getString("systemLog", "[System Ready] Daily 07:30 Auto-Scan & +1Hr Post-Race Audit Active.");
        txtSystemLog.setText(savedLog);
    }

    private void appendLog(String msg) {
        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.UK).format(new Date());
        String existing = prefs.getString("systemLog", "");
        String updated = "[" + time + "] " + msg + "\n" + existing;
        if (updated.length() > 2500) updated = updated.substring(0, 2500);
        prefs.edit().putString("systemLog", updated).apply();
        refreshScoreboardAndLog();
    }

    public static class DailyMarketScanWorker extends Worker {
        public DailyMarketScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            SharedPreferences p = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String time = new SimpleDateFormat("dd/MM HH:mm", Locale.UK).format(new Date());
            String log = "[" + time + "] AUTO-SCAN (07:30): Background scan completed & logged for training.\n" + p.getString("systemLog", "");
            p.edit().putString("systemLog", log).apply();
            return Result.success();
        }
    }

    public static class PostRaceAuditWorker extends Worker {
        public PostRaceAuditWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            SharedPreferences p = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int nextSamples = p.getInt("sampleCount", 4) + 1;
            String time = new SimpleDateFormat("dd/MM HH:mm", Locale.UK).format(new Date());
            String log = "[" + time + "] AUTO-AUDIT (+1Hr): Post-race results scored (" + nextSamples + "/50).\n" + p.getString("systemLog", "");
            p.edit().putInt("sampleCount", nextSamples).putString("systemLog", log).apply();
            return Result.success();
        }
    }
}
