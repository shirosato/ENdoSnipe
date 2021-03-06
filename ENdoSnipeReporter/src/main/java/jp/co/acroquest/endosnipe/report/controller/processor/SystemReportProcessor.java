/*
 * Copyright (c) 2004-2009 SMG Co., Ltd. All Rights Reserved.
 * Please read the associated COPYRIGHTS file for more details.
 *
 * THE  SOFTWARE IS  PROVIDED BY  SMG Co., Ltd., WITHOUT  WARRANTY  OF
 * ANY KIND,  EXPRESS  OR IMPLIED,  INCLUDING BUT  NOT LIMITED  TO THE
 * WARRANTIES OF  MERCHANTABILITY,  FITNESS FOR A  PARTICULAR  PURPOSE
 * AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDER BE LIABLE FOR ANY
 * CLAIM, DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING
 * OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */
package jp.co.acroquest.endosnipe.report.controller.processor;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import jp.co.acroquest.endosnipe.report.LogIdConstants;
import jp.co.acroquest.endosnipe.report.ReporterPluginProvider;
import jp.co.acroquest.endosnipe.report.controller.ReportProcessReturnContainer;
import jp.co.acroquest.endosnipe.report.controller.ReportSearchCondition;
import jp.co.acroquest.endosnipe.report.controller.ReportType;
import jp.co.acroquest.endosnipe.report.controller.TemplateFileManager;
import jp.co.acroquest.endosnipe.report.dao.SystemRecordAccessor;
import jp.co.acroquest.endosnipe.report.entity.SystemResourceRecord;
import jp.co.acroquest.endosnipe.report.output.ResourceReporter;
import jp.co.acroquest.endosnipe.report.util.ReporterConfigAccessor;
import jp.co.acroquest.endosnipe.common.logger.ENdoSnipeLogger;
import jp.co.acroquest.endosnipe.report.controller.processor.ReportPublishProcessorBase;
import jp.co.acroquest.endosnipe.report.controller.processor.SystemReportProcessor;

/**
 * CPU/メモリの使用率レポートを生成するレポートプロセッサ
 * 
 * @author eriguchi
 */
public class SystemReportProcessor extends ReportPublishProcessorBase
{
    /** ロガー */
    private static final ENdoSnipeLogger LOGGER = ENdoSnipeLogger.getLogger(
            SystemReportProcessor.class, ReporterPluginProvider.INSTANCE);

    /**
     * プロセッサを生成する。
     * 
     * @param type このプロセッサが扱うレポートの種類。
     */
    public SystemReportProcessor(ReportType type)
    {
        super(type);
    }

    /**
     * レポートデータを取得する。
     * 
     * @param cond 検索条件。
     * @param reportContainer レポート出力中に発生した補足情報を格納する汎用コンテナ。
     * @return レポートデータ
     */
    @Override
    protected Object getReportPlotData(ReportSearchCondition cond,
            ReportProcessReturnContainer reportContainer)
    {
        // 検索条件の取得
        String database = cond.getDatabases().get(0);
        Timestamp startTime = cond.getStartDate();
        Timestamp endTime = cond.getEndDate();

        // DBから検索
        SystemRecordAccessor accessor = new SystemRecordAccessor();
        List<SystemResourceRecord> data;
        try
        {
            data = accessor.findSystemResourceStaticsByTerm(database, startTime, endTime);
        }
        catch (SQLException ex)
        {
            LOGGER.log(LogIdConstants.EXCEPTION_IN_READING, ex,
                    ReporterConfigAccessor.getReportName(getReportType()));
            return null;
        }

        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object convertPlotData(Object rawData, ReportSearchCondition cond,
            ReportProcessReturnContainer reportContainer)
    {
        List<SystemResourceRecord> data = (List<SystemResourceRecord>)rawData;
        return (SystemResourceRecord[])data.toArray(new SystemResourceRecord[data.size()]);
    }

    /**
     * レポートを出力する。
     * 
     * @param plotData レポートデータ。
     * @param cond レポートデータを取得した際の検索条件。
     * @param reportContainer レポート出力中に発生した補足情報を格納する汎用コンテナ。
     */
    @Override
    protected void outputReport(Object plotData, ReportSearchCondition cond,
            ReportProcessReturnContainer reportContainer)
    {
        if ((plotData instanceof SystemResourceRecord[]) == false)
        {
            return;
        }

        // 出力するレポートの種類にあわせてテンプレートのファイルパスを取得する
        String templateFilePath;
        try
        {
            templateFilePath = TemplateFileManager.getInstance().getTemplateFile(getReportType());
        }
        catch (IOException exception)
        {
            reportContainer.setHappendedError(exception);
            return;
        }

        // レポート出力の引数情報を取得する
        SystemResourceRecord[] records = (SystemResourceRecord[])plotData;
        String outputFilePath = getOutputFileName();
        Timestamp startTime = cond.getStartDate();
        Timestamp endTime = cond.getEndDate();

        // レポート出力を実行する
        ResourceReporter<SystemResourceRecord> reporter =
                new ResourceReporter<SystemResourceRecord>(this.getReportType());
        reporter.outputReport(templateFilePath, outputFilePath, records, startTime, endTime);
    }

}
