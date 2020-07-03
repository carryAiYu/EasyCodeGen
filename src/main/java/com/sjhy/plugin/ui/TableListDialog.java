package com.sjhy.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.sjhy.plugin.constants.MsgValue;
import com.sjhy.plugin.tool.CacheDataUtils;
import com.sjhy.plugin.tool.StringUtils;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class TableListDialog extends JDialog {
    private Project project;
    private JPanel contentPane;
    private JList<String> list1;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JButton button1;
    private final DefaultListModel<String> listModel;

    public TableListDialog(Project project) {
        this.project = project;
        setContentPane(contentPane);
        setModal(true);
        listModel = new DefaultListModel<>();
        readTableList().forEach(listModel::addElement);
        list1.setModel(listModel);

        list1.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) { }
            @Override
            public void mousePressed(MouseEvent e) { }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    System.out.println("Double CLICK mouseReleased");
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) { }
            @Override
            public void mouseExited(MouseEvent e) { }
        });

        buttonOK.addActionListener(
                e -> {
                    if (list1.getSelectedValuesList().isEmpty()) {
                        Messages.showWarningDialog("没有选择任何表", MsgValue.TITLE_INFO);
                    } else {
                        CacheDataUtils.getInstance().setDbTableList(list1.getSelectedValuesList());
                        new SelectSavePath(project).open();
                    }
                }
        );

        buttonCancel.addActionListener(
                e -> {
                    if (list1.getSelectedValuesList().size() > 1) {
                        Messages.showWarningDialog("不能多表编辑", MsgValue.TITLE_INFO);
                    } else {
                        if (!list1.getSelectedValuesList().isEmpty()) {
                            CacheDataUtils.getInstance().setSelectDbTable(
                                    list1.getSelectedValue()
                            );
                            new ConfigTableDialog(project).open();
                        } else {
                            Messages.showWarningDialog("没有选择任何表", MsgValue.TITLE_INFO);
                        }
                    }
                }
        );
        initEvent();
    }


    /**
     * 保存的相对路径
     */
    private static final String SAVE_PATH = "/.idea/EasyCodeConfig";

    private List<String> readTableList() {
        // 获取保存的目录
        String path = project.getBasePath() + SAVE_PATH;
        File dir = new File(path);
        // 文件不存在时直接返回
        List<String> tableList = new ArrayList<>();
        if (!dir.exists()) {
            return tableList;
        }
        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            String[] fileNames = fileName.split("\\.");
            if (fileNames.length > 0) {
                fileName = fileNames[0];
                fileNames = fileName.split("-");
                if (fileNames.length > 1) {
                    tableList.add(fileNames[1]);
                }
            }
        }
        return tableList;
    }

    private void initEvent() {
        button1.addActionListener(e -> {
            String value = Messages.showInputDialog("Table Name:", "Input Table Name:", Messages.getQuestionIcon(), "Table1", new InputValidator() {
                @Override
                public boolean checkInput(String inputString) {
                    if (StringUtils.isEmpty(inputString)) {
                        return false;
                    }
                    Enumeration<String> list = listModel.elements();
                    while (list.hasMoreElements()) {
                        if (list.nextElement().equals(inputString)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public boolean canClose(String inputString) {
                    return this.checkInput(inputString);
                }
            });
            //取消输入
            if (value == null) {
                return;
            }
            listModel.addElement(value);
        });
    }


    /**
     * 打开窗口
     */
    public void open() {
        setTitle("表信息列表");
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

}
