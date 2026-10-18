package com.menghanstudio.gooutpls;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.RuleEngine;
import com.menghanstudio.gooutpls.core.RuleRepository;
import com.menghanstudio.gooutpls.ui.BaseActivity;

import java.util.UUID;

public class RuleEditActivity extends BaseActivity {

    public static final String EXTRA_RULE_ID = "extra_rule_id";

    private EditText etName;
    private EditText etExpression;
    private TextView tvError;
    private TextView tvTitle;

    private String ruleId;
    private RuleRepository.Rule existing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_edit);
        applySystemBarInsets(findViewById(R.id.root));

        etName = findViewById(R.id.etRuleName);
        etExpression = findViewById(R.id.etRuleExpression);
        tvError = findViewById(R.id.tvRuleError);
        tvTitle = findViewById(R.id.tvRuleEditTitle);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveRule).setOnClickListener(v -> saveRule());

        ruleId = getIntent().getStringExtra(EXTRA_RULE_ID);
        if (ruleId != null) {
            existing = new RuleRepository(this).find(ruleId);
            if (existing == null) {
                finish();
                return;
            }
            tvTitle.setText(R.string.rule_edit_title);
            etName.setText(existing.name);
            etExpression.setText(existing.expression);
            etExpression.setSelection(etExpression.getText().length());
        }
    }

    private void saveRule() {
        String name = etName.getText().toString().trim();
        String expression = etExpression.getText().toString().trim();
        if (name.isEmpty()) {
            showError(getString(R.string.rule_error_name));
            return;
        }
        if (name.length() > RuleRepository.MAX_NAME_LENGTH) {
            showError(getString(R.string.rule_error_name_long));
            return;
        }
        if (expression.isEmpty()) {
            showError(getString(R.string.rule_error_expr));
            return;
        }
        if (ruleId == null && new RuleRepository(this).count() >= RuleRepository.MAX_RULES) {
            showError(getString(R.string.rule_error_limit));
            return;
        }

        RuleEngine.ValidationResult validation = RuleEngine.validate(expression);
        if (!validation.valid) {
            showError(validation.error);
            return;
        }

        RuleRepository repository = new RuleRepository(this);
        String id = ruleId == null ? UUID.randomUUID().toString() : ruleId;
        long createdAt = existing == null ? System.currentTimeMillis() : existing.createdAt;
        boolean enabled = existing == null || existing.enabled;
        boolean saved = repository.upsert(new RuleRepository.Rule(
                id, name, expression, enabled, createdAt));
        if (!saved) {
            showError(getString(R.string.rule_error_limit));
            return;
        }

        Toast.makeText(this, R.string.rule_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showError(String message) {
        tvError.setText(message == null ? getString(R.string.rule_error_expr) : message);
        tvError.setVisibility(View.VISIBLE);
    }
}
