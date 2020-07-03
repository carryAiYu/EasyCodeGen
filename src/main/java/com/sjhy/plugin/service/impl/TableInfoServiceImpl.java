package com.sjhy.plugin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ExceptionUtil;
import com.sjhy.plugin.constants.MsgValue;
import com.sjhy.plugin.entity.ColumnInfo;
import com.sjhy.plugin.entity.SaveFile;
import com.sjhy.plugin.entity.TableInfo;
import com.sjhy.plugin.entity.TypeMapper;
import com.sjhy.plugin.service.TableInfoService;
import com.sjhy.plugin.tool.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * @author makejava
 * @version 1.0.0
 * @since 2018/09/02 12:13
 */
public class TableInfoServiceImpl implements TableInfoService {
    /**
     * 项目对象
     */
    private Project project;

    /**
     * 命名工具类
     */
    private NameUtils nameUtils;
    /**
     * jackson格式化工具
     */
    private ObjectMapper objectMapper;
    /**
     * 文件工具类
     */
    private FileUtils fileUtils;

    /**
     * 保存的相对路径
     */
    private static final String SAVE_PATH = "/.idea/EasyCodeConfig";

    public TableInfoServiceImpl(Project project) {
        this.project = project;
        this.nameUtils = NameUtils.getInstance();
        this.objectMapper = new ObjectMapper();
        this.fileUtils = FileUtils.getInstance();
    }

    /**
     * 通过DbTable获取TableInfo
     *
     * @return 表信息对象
     */
    @Override
    public TableInfo getTableInfoByDbTable(String tableName) {
        TableInfo tableInfo = new TableInfo();
        // 设置类名
        tableInfo.setName(tableName);
        // 设置所有列
        tableInfo.setFullColumn(new ArrayList<>());
        // 设置主键列
        tableInfo.setPkColumn(new ArrayList<>());
        // 设置其他列
        tableInfo.setOtherColumn(new ArrayList<>());
        return tableInfo;
    }

    /**
     * 获取表信息并加载配置信息
     *
     * @param dbTable 原始表对象
     * @return 表信息对象
     */
    @Override
    public TableInfo getTableInfoAndConfig(String tableName) {
        TableInfo tableInfo = this.getTableInfoByDbTable(tableName);
        // 加载配置
        this.loadConfig(tableInfo);
        return tableInfo;
    }

    /**
     * 加载单个表信息配置(用户自定义列与扩张选项)
     *
     * @param tableInfo 表信息对象
     */
    private void loadConfig(TableInfo tableInfo) {
        if (tableInfo == null) {
            return;
        }
        // 读取配置文件中的表信息
        TableInfo tableInfoConfig = read(tableInfo);
        // 返回空直接不处理
        if (tableInfoConfig == null) {
            return;
        }
        // 开始合并数据
        // 选择模型名称
        tableInfo.setSaveModelName(tableInfoConfig.getSaveModelName());
        // 选择的包名
        tableInfo.setSavePackageName(tableInfoConfig.getSavePackageName());
        // 选择的保存路径
        tableInfo.setSavePath(tableInfoConfig.getSavePath());
        // 选择的表名前缀
        tableInfo.setPreName(tableInfoConfig.getPreName());
        // 选择的模板组
        tableInfo.setTemplateGroupName(tableInfoConfig.getTemplateGroupName());

        // 没有列时不处理
        if (CollectionUtil.isEmpty(tableInfoConfig.getFullColumn())) {
            return;
        }

        int fullSize = tableInfoConfig.getFullColumn().size();
        // 所有列
        List<ColumnInfo> fullColumn = new ArrayList<>(fullSize);
        int pkSize = tableInfo.getPkColumn().size();
        // 主键列
        List<ColumnInfo> pkColumn = new ArrayList<>(pkSize);
        // 其他列
        List<ColumnInfo> otherColumn = new ArrayList<>(fullSize - pkSize);

        for (ColumnInfo column : tableInfo.getFullColumn()) {
            boolean exists = false;
            for (ColumnInfo configColumn : tableInfoConfig.getFullColumn()) {
                if (Objects.equals(configColumn.getName(), column.getName())) {
                    exists = true;
                    // 覆盖空值
                    if (configColumn.getType() == null) {
                        configColumn.setType(column.getType());
                    }
                    if (!StringUtils.isEmpty(configColumn.getType())) {
                        // 短类型
                        configColumn.setShortType(nameUtils.getClsNameByFullName(configColumn.getType()));
                    }
                    // 表注释覆盖
                    if (configColumn.getComment() == null) {
                        configColumn.setComment(column.getComment());
                    }
                    // 添加至新列表中
                    fullColumn.add(configColumn);
                    break;
                }
            }
            // 新增的列
            if (!exists) {
                fullColumn.add(column);
            }
        }
        // 添加附加列
        for (ColumnInfo configColumn : tableInfoConfig.getFullColumn()) {
            if (configColumn.isCustom()) {
                fullColumn.add(configColumn);
            }
        }

        // 全部覆盖
        tableInfo.setFullColumn(fullColumn);
        tableInfo.setPkColumn(pkColumn);
        tableInfo.setOtherColumn(otherColumn);
    }

    /**
     * 通过映射获取对应的java类型类型名称
     *
     * @param typeName 列类型
     * @return java类型
     */
    private String getColumnType(String typeName) {
        for (TypeMapper typeMapper : CurrGroupUtils.getCurrTypeMapperGroup().getElementList()) {
            // 不区分大小写进行类型转换
            if (typeMapper.getColumnType().equals(typeName)) {
                return typeMapper.getJavaType();
            }
        }
        // 没找到直接返回Object
        return "java.lang.Object";
    }

    /**
     * 保存数据
     *
     * @param tableInfo 表信息对象
     */
    @Override
    public void save(TableInfo tableInfo) {
        // 克隆对象，防止串改，同时原始对象丢失
        tableInfo = CloneUtils.cloneByJson(tableInfo, false);
        //排除部分字段，这些字段不进行保存
        tableInfo.setOtherColumn(null);
        tableInfo.setPkColumn(null);
        // 获取迭代器
        for (ColumnInfo columnInfo : tableInfo.getFullColumn()) {
            // 扩展项不能为空
            if (columnInfo.getExt() == null) {
                columnInfo.setExt(new LinkedHashMap<>());
            }
            columnInfo.setType(getColumnType(columnInfo.getJdbcType()));
        }
        // 获取优雅格式的JSON字符串
        String content = null;
        try {
            content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tableInfo);
        } catch (JsonProcessingException e) {
            ExceptionUtil.rethrow(e);
        }
        if (content == null) {
            Messages.showWarningDialog("保存失败，JSON序列化错误。", MsgValue.TITLE_INFO);
            return;
        }
        // 获取或创建保存目录
        String path = project.getBasePath() + SAVE_PATH;
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Messages.showWarningDialog("保存失败，无法创建目录。", MsgValue.TITLE_INFO);
                return;
            }
        }
        // 获取保存文件
        new SaveFile(project, dir.getAbsolutePath(), getConfigFileName(tableInfo.getName()),
                content, true, false).write();
    }

    /**
     * 读取配置文件
     *
     * @param tableInfo 表信息对象
     * @return 读取到的配置信息
     */
    private TableInfo read(TableInfo tableInfo) {
        // 获取保存的目录
        String path = project.getBasePath() + SAVE_PATH;
        File dir = new File(path);
        // 获取保存的文件
        File file = new File(dir, getConfigFileName(tableInfo.getName()));
        // 文件不存在时直接返回
        if (!file.exists()) {
            return null;
        }
        // 读取并解析文件
        String json = fileUtils.read(project, file);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        return parser(json);
    }

    /**
     * 获取配置文件名称
     *
     * @param dbTable 表信息对象
     * @return 对应的配置文件名称
     */
    private String getConfigFileName(String tableName) {
        String schemaName = "schema";
        return schemaName + "-" + tableName + ".json";
    }

    /**
     * 对象还原
     *
     * @param str 原始JSON字符串
     * @return 解析结果
     */
    private TableInfo parser(String str) {
        try {
            return objectMapper.readValue(str, TableInfo.class);
        } catch (IOException e) {
            Messages.showWarningDialog("读取配置失败，JSON反序列化异常。", MsgValue.TITLE_INFO);
            ExceptionUtil.rethrow(e);
        }
        return null;
    }
}
