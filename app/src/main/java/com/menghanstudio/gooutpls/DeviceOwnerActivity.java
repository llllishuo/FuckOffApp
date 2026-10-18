package com.menghanstudio.gooutpls;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.DeviceOwnerTools;
import com.menghanstudio.gooutpls.ui.BaseActivity;

public class DeviceOwnerActivity extends BaseActivity {

    private TextView tvDoStatus;
    private TextView tvDoStatusDesc;
    private TextView tvCommand;
    private TextView btnCheckOwner;
    private View cardClearOwner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_owner);
        applySystemBarInsets(findViewById(R.id.root));

        tvDoStatus = findViewById(R.id.tvDoStatus);
        tvDoStatusDesc = findViewById(R.id.tvDoStatusDesc);
        tvCommand = findViewById(R.id.tvCommand);
        btnCheckOwner = findViewById(R.id.btnCheckOwner);
        cardClearOwner = findViewById(R.id.cardClearOwner);

        tvCommand.setText(DeviceOwnerTools.provisioningCommand(this));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCopyCommand).setOnClickListener(v -> copyCommand());
        findViewById(R.id.btnClearOwner).setOnClickListener(v -> confirmClearOwner());
        btnCheckOwner.setOnClickListener(v -> {
            refreshStatus(true);
        });

        refreshStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus(false);
    }

    private void refreshStatus(boolean userAction) {
        boolean owner = DeviceOwnerTools.isDeviceOwner(this);
        DeviceOwnerTools.syncOwnerState(this);

        tvDoStatus.setText(owner ? R.string.do_ready : R.string.do_not_ready);
        tvDoStatus.setTextColor(getColor(owner ? R.color.teal : R.color.amber));
        tvDoStatusDesc.setText(owner
                ? R.string.do_ready_desc : R.string.do_not_ready_desc);
        cardClearOwner.setVisibility(owner ? View.VISIBLE : View.GONE);
        btnCheckOwner.setText(R.string.do_check);

        if (userAction) {
            Toast.makeText(this, owner ? R.string.toast_do_ready : R.string.toast_do_not_ready,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void copyCommand() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "FuckOff adb command", tvCommand.getText().toString()));
        Toast.makeText(this, R.string.toast_command_copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmClearOwner() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.do_clear_confirm_title)
                .setMessage(R.string.do_clear_confirm_msg)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm_cancel, (dialog, which) -> {
                    boolean cleared = DeviceOwnerTools.clearDeviceOwner(this);
                    if (cleared) {
                        Toast.makeText(this, R.string.toast_owner_cleared,
                                Toast.LENGTH_LONG).show();
                        refreshStatus(false);
                    } else {
                        Toast.makeText(this, R.string.toast_owner_clear_failed,
                                Toast.LENGTH_LONG).show();
                        refreshStatus(false);
                    }
                })
                .show();
    }
}
