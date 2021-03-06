package com.twiceyuan.ssrules.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatSpinner;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;

import com.rengwuxian.materialedittext.MaterialEditText;
import com.twiceyuan.autofinder.AutoFinder;
import com.twiceyuan.ssrules.R;
import com.twiceyuan.ssrules.constants.Filters;
import com.twiceyuan.ssrules.constants.Formats;
import com.twiceyuan.ssrules.helper.AclHelper;
import com.twiceyuan.ssrules.helper.Preferences;
import com.twiceyuan.ssrules.helper.ViewHelper;
import com.twiceyuan.ssrules.model.AclFile;
import com.twiceyuan.ssrules.model.AclType;
import com.twiceyuan.ssrules.ui.constract.CanBack;
import com.twiceyuan.ssrules.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import rx.functions.Func1;

/**
 * Created by twiceYuan on 05/12/2016.
 * Email: i@twiceyuan.com
 * Site: http://twiceyuan.com
 * <p>
 * 接收分享结果添加到规则
 */
public class ShareReceiveActivity extends BaseActivity implements CanBack {

    private static final int MENU_SUBMIT = 1;

    // 规则 domain host
    MaterialEditText et_host;
    // 规则内容（生成）
    MaterialEditText et_rule;
    // 文件下拉菜单
    AppCompatSpinner sp_file;
    // 类型下拉菜单
    AppCompatSpinner sp_type;

    private List<String> mSelectedFileContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_receive);

        AutoFinder.find(this);

        initViews();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent); // Handle text being sent
            }
        } else {
            Utils.toast("没有解析到 URL");
            finish();
        }
    }

    private void initViews() {
        ViewHelper.watchText(et_host, s -> {
            if (s.length() == 0) {
                return;
            }

            String text = "(.*\\.)?" + s.replaceAll("\\.", "\\\\.");
            et_rule.setText(text);
        });

        List<AclFile> files = AclHelper.getAllAclFiles();

        sp_file.setAdapter(new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, files));
        ViewHelper.spinnerSelect(sp_file, position -> {
            String filePath = files.get(position).filePath;
            AclHelper.readFile(filePath).subscribe(strings -> {

                mSelectedFileContent = strings;

                List<AclType> typeList = new ArrayList<>();
                for (int i = 0; i < strings.size(); i++) {
                    String line = strings.get(i);
                    if (line.startsWith("[") && Filters.TYPE.keySet().contains(line)) {
                        AclType type = new AclType();
                        type.content = line;
                        type.name = Filters.TYPE.get(line);
                        typeList.add(type);
                    }
                }

                sp_type.setAdapter(new ArrayAdapter<>(ShareReceiveActivity.this,
                        R.layout.support_simple_spinner_dropdown_item,
                        typeList)
                );

                sp_type.setVisibility(View.VISIBLE);

                String lastType = Preferences.getSetting(Preferences.Key.LAST_TYPE, "");
                ViewHelper.setDefaultOrFirst(sp_type, lastType, new Func1<AclType, String>() {
                    @Override
                    public String call(AclType aclType) {
                        return aclType.content;
                    }
                });
            });
        });
        String lastFilePath = Preferences.getSetting(Preferences.Key.LAST_FILE, "");
        ViewHelper.setDefaultOrFirst(sp_file, lastFilePath, new Func1<AclFile, String>() {
            @Override
            public String call(AclFile file) {
                return file.filePath;
            }
        });
    }

    void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null && Formats.isURL(sharedText)) {
            String host = Uri.parse(sharedText).getHost();
            et_host.setText(host);
        } else {
            Utils.toast("没有解析到 URL");
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_SUBMIT, 0, "添加")
                .setIcon(R.drawable.ic_submit_white_24dp)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SUBMIT) {

            if (!checkValidate()) {
                return true;
            }

            AclFile selectedFile = (AclFile) sp_file.getSelectedItem();
            AclType selectedType = (AclType) sp_type.getSelectedItem();

            AclHelper.insertAcl(this,
                    et_rule.getText().toString(),
                    selectedFile.filePath,
                    selectedType.content
            ).subscribe(aVoid -> {
                finish();
            });

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 检查数据合法性
     */
    private boolean checkValidate() {
        if (et_host.length() == 0) {
            Utils.toast("规则不能为空");
            return false;
        }

        if (sp_file.getSelectedItem() == null) {
            Utils.toast("文件不能为空");
            return false;
        }

        if (sp_type.getSelectedItem() == null) {
            Utils.toast("规则类型不能为空");
            return false;
        }

        if (mSelectedFileContent != null && mSelectedFileContent.contains(et_rule.getText().toString())) {
            Utils.toast("该规则已存在");
            return false;
        }

        return true;
    }
}
