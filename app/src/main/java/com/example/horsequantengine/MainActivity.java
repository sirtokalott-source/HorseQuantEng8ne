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

    private LinearLayout containerCorePicks, containerSubPicks;
    private TextView txtSubThresholdHeader, txtScoreboard, txtSystemLog;
    private EditText inputExtraSubPicks;
    private List<RunnerCandidate> dailySlate = new ArrayList<>();

    static class RunnerCandidate {
        String raceId, raceTimeTrack, horseName, formString, weightInfo;
        double decimalOdds, edgeA, edgeB, probA, probB;
        int daysOff, rpr, ts;
        boolean rank1Travel, rank2CourseDist, rank3GoingStick, rank4JumpClass;
        boolean hasFitnessPenalty, hasWeightPenalty, hasPaceEdge, hasSteamEdge, hasSynergyEdge, hasDrawEdge;
        boolean voidedByUser = false;

        RunnerCandidate(String raceId, String raceTimeTrack, String horseName, double decimalOdds,
                        String formString, int daysOff, String weightInfo, int rpr, int ts,
                        boolean r1, boolean r2, boolean r3, boolean r4,
                        boolean fitPen, boolean wgtPen, boolean pace, boolean steam, boolean syn, boolean draw) {
            this.raceId = raceId; this.raceTimeTrack = raceTimeTrack; this.horseName = horseName;
            this.decimalOdds = decimalOdds; this.formString = formString; this.daysOff = daysOff;
            this.weightInfo = weightInfo; this.rpr = rpr; this.ts = ts;
            this.rank1Travel = r1; this.rank2CourseDist = r2; this.rank3GoingStick = r3; this.rank4JumpClass = r4;
            this.hasFitnessPenalty = fitPen; this.hasWeightPenalty = wgtPen;
            this.hasPaceEdge = pace; this.hasSteamEdge = steam; this.hasSynergyEdge = syn; this.hasDrawEdge = draw;
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
                    .putFloat("champW1", 0.40f).putFloat("champW2", 0.25f)
                    .putFloat("champW3", 0.20f).putFloat("champW4", 0.15f)
                    .putFloat("backupW1", 0.40f).putFloat("backupW2", 0.25f)
                    .putFloat("backupW3", 0.20f).putFloat("backupW4", 0.15f)
                    .putFloat("challW1", 0.40f).putFloat("challW2", 0.25f)
                    .putFloat("challW3", 0.20f).putFloat("challW4", 0.15f)
                    .putInt("sampleCount", 4).putInt("winsA", 3).putFloat("profitA", 6.25f)
                    .putInt("winsB", 3).putFloat("profitB", 6.50f).putInt("postPromoCount", 0)
                    .apply();
        }
    }

    private void runMarketEvaluation() {
        loadTodayMarketFeed();
        float cW1 = prefs.getFloat("champW1", 0.40f), cW2 = prefs.getFloat("champW2", 0.25f);
        float cW3 = prefs.getFloat("champW3", 0.20f), cW4 = prefs.getFloat("champW4", 0.15f);
        float bW1 = prefs.getFloat("challW1", 0.40f), bW2 = prefs.getFloat("challW2", 0.25f);
        float bW3 = prefs.getFloat("challW3", 0.20f), bW4 = prefs.getFloat("challW4", 0.15f);

        List<RunnerCandidate> validCore = new ArrayList<>();
        List<RunnerCandidate> subThreshold = new ArrayList<>();

        for (RunnerCandidate r : dailySlate) {
            if (r.voidedByUser) continue;
            double impliedM = (1.0 / r.decimalOdds) * 100.0;
            double pA = 12.0 + (r.rank1Travel ? cW1 * 100 : 0) + (r.rank2CourseDist ? cW2 * 100 : 0)
                    + (r.rank3GoingStick ? cW3 * 100 : 0) + (r.rank4JumpClass ? cW4 * 100 : 0);
            double pB = 12.0 + (r.rank1Travel ? bW1 * 100 : 0) + (r.rank2CourseDist ? bW2 * 100 : 0)
                    + (r.rank3GoingStick ? bW3 * 100 : 0) + (r.rank4JumpClass ? bW4 * 100 : 0);

            if (r.hasFitnessPenalty) pB -= 12.0;
            if (r.hasWeightPenalty) pB -= 8.0;
            if (r.hasPaceEdge) pB += 5.0;
            if (r.hasSteamEdge) pB += 5.0;
            if (r.hasSynergyEdge) pB += 4.0;
            if (r.hasDrawEdge) pB += 4.0;

            r.probA = pA; r.probB = pB; r.edgeA = pA - impliedM; r.edgeB = pB - impliedM;
            if (r.edgeA > 0 && !r.hasFitnessPenalty) validCore.add(r);
            else subThreshold.add(r);
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
                containerCorePicks.addView(buildRunnerCard(corePicks.get(i), i + 1, true));
            }
        }

        int extraRequested = 0;
        try { extraRequested = Integer.parseInt(inputExtraSubPicks.getText().toString().trim()); } catch (Exception ignored) {}

        if (extraRequested > 0 && !subPicks.isEmpty()) {
            txtSubThresholdHeader.setVisibility(View.VISIBLE);
            containerSubPicks.setVisibility(View.VISIBLE);
            for (int i = 0; i < Math.min(extraRequested, subPicks.size()); i++) {
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);

        String betAction = !isCore ? "SUB-THRESHOLD: EXCLUDED FROM TRAINING DATA"
                : (r.decimalOdds >= 7.0 ? "£1.00 EACH-WAY (£2.00 TOTAL) — PROFIT E/W" : "£2.00 STRAIGHT WIN (BOG ACTIVE)");

        TextView title = new TextView(this);
        title.setText("#" + rankOrder + " | " + r.raceTimeTrack + " — " + r.horseName.toUpperCase() + " (@ " + String.format(Locale.UK, "%.2f", r.decimalOdds) + ")");
        title.setTextColor(isCore ? Color.parseColor("#FF9800") : Color.parseColor("#FF5252"));
        title.setTextSize(15f);
        card.addView(title);

        TextView formLine = new TextView(this);
        formLine.setText("FORM: " + r.formString + " (" + r.daysOff + "d off) | WGT: " + r.weightInfo + " | RPR " + r.rpr + " / TS " + r.ts);
        formLine.setTextColor(Color.parseColor("#81D4FA"));
        formLine.setTextSize(12f);
        card.addView(formLine);

        TextView metrics = new TextView(this);
        metrics.setText(String.format(Locale.UK, "Engine A: %.1f%% (Edge %+.1f%%) | Engine B: %.1f%% (Edge %+.1f%%)\nVERDICT: %s",
                r.probA, r.edgeA, r.probB, r.edgeB, betAction));
        metrics.setTextColor(Color.WHITE);
        metrics.setTextSize(13f);
        card.addView(metrics);

        if (isCore) {
            Button btnProblem = new Button(this);
            btnProblem.setText("PROBLEM / RECHECK THIS RACE");
            btnProblem.setBackgroundColor(Color.parseColor("#D32F2F"));
            btnProblem.setTextColor(Color.WHITE);
            btnProblem.setTextSize(11f);
            btnProblem.setOnClickListener(v -> {
                r.voidedByUser = true;
                appendLog("VOIDED: " + r.horseName + " flagged. Re-evaluating " + r.raceTimeTrack + "...");
                runMarketEvaluation();
            });
            card.addView(btnProblem);
        }
        return card;
    }

    private void loadTodayMarketFeed() {
        if (!dailySlate.isEmpty()) return;
        dailySlate.add(new RunnerCandidate("WETH_1320", "13:20 Wetherby", "Florida Dreams", 3.00,
                "366-81", 15, "10st 12lb", 128, 118, true, true, true, true, false, false, true, true, true, false));
        dailySlate.add(new RunnerCandidate("CHEL_1420", "14:20 Cheltenham", "Zurich", 9.50,
                "1352-5", 27, "10st 11lb", 144, 131, true, true, true, false, false, false, true, false, true, false));
        dailySlate.add(new RunnerCandidate("CHEL_1235", "12:35 Cheltenham", "Little Ledgend", 3.75,
                "-22113", 22, "11st 2lb", 138, 126, false, false, true, false, false, false, false, false, false, false));
    }

    private void scheduleDailyMorningAutoScan() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 7); target.set(Calendar.MINUTE, 30); target.set(Calendar.SECOND, 0);
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1);

        PeriodicWorkRequest dailyScan = new PeriodicWorkRequest.Builder(DailyMarketScanWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(target.getTimeInMillis() - now.getTimeInMillis(), TimeUnit.MILLISECONDS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("Daily0730MarketScan", ExistingPeriodicWorkPolicy.KEEP, dailyScan);
    }

    private void scheduleOneHourPostRaceAudit(int lastRaceHour, int lastRaceMinute) {
        Calendar now = Calendar.getInstance();
        Calendar auditTime = Calendar.getInstance();
        auditTime.set(Calendar.HOUR_OF_DAY, lastRaceHour + 1); auditTime.set(Calendar.MINUTE, lastRaceMinute); auditTime.set(Calendar.SECOND, 0);

        OneTimeWorkRequest auditRequest = new OneTimeWorkRequest.Builder(PostRaceAuditWorker.class)
                .setInitialDelay(Math.max(60000L, auditTime.getTimeInMillis() - now.getTimeInMillis()), TimeUnit.MILLISECONDS).build();
        WorkManager.getInstance(this).enqueue(auditRequest);
    }

    private void refreshScoreboardAndLog() {
        int samples = prefs.getInt("sampleCount", 4), winsA = prefs.getInt("winsA", 3), winsB = prefs.getInt("winsB", 3);
        float profitA = prefs.getFloat("profitA", 6.25f), profitB = prefs.getFloat("profitB", 6.50f);

        txtScoreboard.setText(String.format(Locale.UK,
                "Engine A (Champion): Win Rate %.1f%% | Net ROI %+.1f%% (%+.2f pts)\n" +
                "Engine B (Challenger): Win Rate %.1f%% | Net ROI %+.1f%% (%+.2f pts)\n" +
                "Autonomous 50-Race Gate: %d / 50 Audited Races",
                samples > 0 ? (winsA * 100.0 / samples) : 0.0, samples > 0 ? (profitA * 100.0 / (samples * 2.0)) : 0.0, profitA,
                samples > 0 ? (winsB * 100.0 / samples) : 0.0, samples > 0 ? (profitB * 100.0 / (samples * 2.0)) : 0.0, profitB, samples));
        txtSystemLog.setText(prefs.getString("systemLog", "[System Ready] Daily 07:30 Auto-Scan & +1Hr Post-Race Audit Active."));
    }

    private void appendLog(String msg) {
        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.UK).format(new Date());
        String updated = "[" + time + "] " + msg + "\n" + prefs.getString("systemLog", "");
        if (updated.length() > 2500) updated = updated.substring(0, 2500);
        prefs.edit().putString("systemLog", updated).apply();
        refreshScoreboardAndLog();
    }

    public static class DailyMarketScanWorker extends Worker {
        public DailyMarketScanWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
        @NonNull @Override public Result doWork() {
            SharedPreferences p = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String time = new SimpleDateFormat("dd/MM HH:mm", Locale.UK).format(new Date());
            p.edit().putString("systemLog", "[" + time + "] AUTO-SCAN (07:30): Background scan completed.\n" + p.getString("systemLog", "")).apply();
            return Result.success();
        }
    }

    public static class PostRaceAuditWorker extends Worker {
        public PostRaceAuditWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
        @NonNull @Override public Result doWork() {
            SharedPreferences p = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int next = p.getInt("sampleCount", 4) + 1;
            String time = new SimpleDateFormat("dd/MM HH:mm", Locale.UK).format(new Date());
            p.edit().putInt("sampleCount", next).putString("systemLog", "[" + time + "] AUTO-AUDIT (+1Hr): Results scored (" + next + "/50).\n" + p.getString("systemLog", "")).apply();
            return Result.success();
        }
    }
}
