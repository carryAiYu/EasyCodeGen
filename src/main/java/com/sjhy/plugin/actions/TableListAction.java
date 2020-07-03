package com.sjhy.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sjhy.plugin.ui.TableListDialog;

public class TableListAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        //开始处理
        new TableListDialog(event.getProject()).open();
    }
}
