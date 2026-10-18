package com.menghanstudio.gooutpls;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.RuleRepository;
import com.menghanstudio.gooutpls.ui.BaseActivity;

import java.util.List;

public class SafetyRulesActivity extends BaseActivity {

    private LinearLayout rulesContainer;
    private TextView tvEmptyRules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_rules);
        applySystemBarInsets(findViewById(R.id.root));

        rulesContainer = findViewById(R.id.rulesContainer);
        tvEmptyRules = findViewById(R.id.tvEmptyRules);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddRule).setOnClickListener(v -> openEditor(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRules();
    }

    private void refreshRules() {
        RuleRepository repository = new RuleRepository(this);
        List<RuleRepository.Rule> rules = repository.getAll();
        rulesContainer.removeAllViews();
        tvEmptyRules.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);

        for (RuleRepository.Rule rule : rules) {
            View row = getLayoutInflater().inflate(
                    R.layout.item_rule, rulesContainer, false);
            TextView name = row.findViewById(R.id.tvRuleName);
            TextView expression = row.findViewById(R.id.tvRuleExpression);
            TextView edit = row.findViewById(R.id.btnEditRule);
            TextView delete = row.findViewById(R.id.btnDeleteRule);
            Switch enabled = row.findViewById(R.id.switchRule);

            name.setText(rule.name == null || rule.name.trim().isEmpty()
                    ? getString(R.string.rule_unnamed)
                    : rule.name);
            expression.setText(rule.expression.replace('\n', ' '));
            enabled.setChecked(rule.enabled);
            enabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                    repository.setEnabled(rule.id, isChecked));
            edit.setOnClickListener(v -> openEditor(rule.id));
            delete.setOnClickListener(v -> confirmDelete(rule));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(10);
            rulesContainer.addView(row, params);
        }
    }

    private void openEditor(String ruleId) {
        Intent intent = new Intent(this, RuleEditActivity.class);
        if (ruleId != null) {
            intent.putExtra(RuleEditActivity.EXTRA_RULE_ID, ruleId);
        }
        startActivity(intent);
    }

    private void confirmDelete(RuleRepository.Rule rule) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_rule_title)
                .setMessage(getString(R.string.delete_rule_msg,
                        rule.name == null || rule.name.trim().isEmpty()
                                ? getString(R.string.rule_unnamed) : rule.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    new RuleRepository(this).delete(rule.id);
                    Toast.makeText(this, R.string.rule_deleted, Toast.LENGTH_SHORT).show();
                    refreshRules();
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
