package com.example.horsequantengine;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ArrayList<String> loggedRaces = new ArrayList<>();
    private double champW1 = 0.40, champW2 = 0.25, champW3 = 0.20, champW4 = 0.15;
    private double challW1 = 0.40, challW2 = 0.25, challW3 = 0.20, challW4 = 0.15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText inputHorseName = findViewById(R.id.inputHorseName);
        EditText inputMarketOdds = findViewById(R.id.inputMarketOdds);
        CheckBox chkRank1 = findViewById(R.id.chkRank1);
        CheckBox chkRank2 = findViewById(R.id.chkRank2);
        CheckBox chkRank3 = findViewById(R.id.chkRank3);
        CheckBox chkRank4 = findViewById(R.id.chkRank4);
        Switch swtSafetyEw = findViewById(R.id.swtSafetyEw);
        RadioButton radBoostTypeA = findViewById(R.id.radBoostTypeA);
        RadioButton radBoostTypeB = findViewById(R.id.radBoostTypeB);
        TextView txtValidationBanner = findViewById(R.id.txtValidationBanner);
        TextView txtFinalOutput = findViewById(R.id.txtFinalOutput);
        Button btnCalculate = findViewById(R.id.btnVerifyAndCalculate);
        Button btnLock = findViewById(R.id.btnLockBaseline);
        Button btnRevert = findViewById(R.id.btnRevertSystem);

        btnCalculate.setOnClickListener(v -> {
            String horseEntry = inputHorseName.getText().toString().trim();
            String oddsText = inputMarketOdds.getText().toString().trim();

            if (horseEntry.isEmpty() || oddsText.isEmpty()) {
                Toast.makeText(this, "Enter horse details and odds", Toast.LENGTH_SHORT).show();
                return;
            }

            if (loggedRaces.contains(horseEntry.toLowerCase())) {
                txtValidationBanner.setText("WARNING: DUPLICATE ENTRY DETECTED");
                txtValidationBanner.setBackgroundColor(0xFFB71C1C);
            } else {
                loggedRaces.add(horseEntry.toLowerCase());
                txtValidationBanner.setText("VALIDATION STATUS: CLEAN (NO DUPLICATE)");
                txtValidationBanner.setBackgroundColor(0xFF1B5E20);
            }

            double decimalOdds = Double.parseDouble(oddsText);
            double impliedMarketProb = (1.0 / decimalOdds) * 100.0;

            double probEngineA = 12.0;
            if (chkRank1.isChecked()) probEngineA += (champW1 * 100.0);
            if (chkRank2.isChecked()) probEngineA += (champW2 * 100.0);
            if (chkRank3.isChecked()) probEngineA += (champW3 * 100.0);
            if (chkRank4.isChecked()) probEngineA += (champW4 * 100.0);

            double probEngineB = 12.0;
            if (chkRank1.isChecked()) probEngineB += (challW1 * 100.0);
            if (chkRank2.isChecked()) probEngineB += (challW2 * 100.0);
            if (chkRank3.isChecked()) probEngineB += (challW3 * 100.0);
            if (chkRank4.isChecked()) probEngineB += (challW4 * 100.0);

            if (radBoostTypeA.isChecked()) probEngineB -= 5.0;
            if (radBoostTypeB.isChecked()) probEngineB -= 15.0;

            double edgeA = probEngineA - impliedMarketProb;
            double edgeB = probEngineB - impliedMarketProb;

            String recommendation;
            if (edgeA > 0) {
                if (decimalOdds >= 7.0) {
                    recommendation = "HIGH-CONVICTION PROFIT E/W BET";
                } else if (decimalOdds >= 6.0 && swtSafetyEw.isChecked()) {
                    recommendation = "SAFETY-NET BREAK-EVEN E/W BET";
                } else {
                    recommendation = "HIGH-CONVICTION STRAIGHT WIN BET";
                }
            } else {
                recommendation = "SUB-THRESHOLD: PASS (NEGATIVE VALUE)";
            }

            String output = "HORSE: " + horseEntry.toUpperCase() + "\n" +
                    "Market Implied Prob (M): " + String.format("%.1f", impliedMarketProb) + "%\n\n" +
                    "ENGINE A (Locked): " + String.format("%.1f", probEngineA) + "% (Edge: " + String.format("%+.1f", edgeA) + "%)\n" +
                    "ENGINE B (Dynamic): " + String.format("%.1f", probEngineB) + "% (Edge: " + String.format("%+.1f", edgeB) + "%)\n\n" +
                    "VERDICT: " + recommendation;

            txtFinalOutput.setText(output);
        });

        btnLock.setOnClickListener(v -> {
            champW1 = challW1;
            champW2 = challW2;
            champW3 = challW3;
            champW4 = challW4;
            Toast.makeText(this, "Engine B locked as new Champion Baseline!", Toast.LENGTH_SHORT).show();
        });

        btnRevert.setOnClickListener(v -> {
            challW1 = champW1;
            challW2 = champW2;
            challW3 = champW3;
            challW4 = champW4;
            Toast.makeText(this, "Engine B reverted to Locked Baseline!", Toast.LENGTH_SHORT).show();
        });
    }
}
