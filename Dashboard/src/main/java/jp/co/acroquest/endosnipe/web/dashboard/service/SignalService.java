package jp.co.acroquest.endosnipe.web.dashboard.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jp.co.acroquest.endosnipe.common.entity.ItemType;
import jp.co.acroquest.endosnipe.common.logger.ENdoSnipeLogger;
import jp.co.acroquest.endosnipe.communicator.CommunicationClient;
import jp.co.acroquest.endosnipe.communicator.entity.Body;
import jp.co.acroquest.endosnipe.communicator.entity.Header;
import jp.co.acroquest.endosnipe.communicator.entity.Telegram;
import jp.co.acroquest.endosnipe.communicator.entity.TelegramConstants;
import jp.co.acroquest.endosnipe.data.dao.JavelinMeasurementItemDao;
import jp.co.acroquest.endosnipe.web.dashboard.constants.LogMessageCodes;
import jp.co.acroquest.endosnipe.web.dashboard.dao.SignalInfoDao;
import jp.co.acroquest.endosnipe.web.dashboard.dto.SignalDefinitionDto;
import jp.co.acroquest.endosnipe.web.dashboard.entity.SignalInfo;
import jp.co.acroquest.endosnipe.web.dashboard.manager.ConnectionClient;
import jp.co.acroquest.endosnipe.web.dashboard.manager.DatabaseManager;

import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * シグナル定義のサービスクラス。
 * 
 * @author miyasaka
 * 
 */
@Service
public class SignalService
{
    /** ロガー。 */
    private static final ENdoSnipeLogger LOGGER = ENdoSnipeLogger.getLogger(MapService.class);

    /**
     * デフォルトのシグナル状態(監視停止中)
     */
    private static final int DEFAULT_SIGNAL_STATE = -1;

    /**
     * シグナル情報Dao
     */
    @Autowired
    protected SignalInfoDao signalInfoDao;

    /**
     * コンストラクタ
     */
    public SignalService()
    {

    }

    /**
     * すべてのシグナルデータを返す。
     * 
     * @return シグナルデータ一覧
     */
    public List<SignalDefinitionDto> getAllSignal()
    {
        List<SignalInfo> signalList = null;
        try
        {
            signalList = signalInfoDao.selectAll();
        }
        catch (PersistenceException pEx)
        {
            Throwable cause = pEx.getCause();
            if (cause instanceof SQLException)
            {
                SQLException sqlEx = (SQLException)cause;
                LOGGER.log(LogMessageCodes.SQL_EXCEPTION, sqlEx, sqlEx.getMessage());
            }
            LOGGER.log(LogMessageCodes.SQL_EXCEPTION, pEx, pEx.getMessage());

            return new ArrayList<SignalDefinitionDto>();
        }

        List<SignalDefinitionDto> definitionDtoList = new ArrayList<SignalDefinitionDto>();

        for (SignalInfo signalInfo : signalList)
        {
            SignalDefinitionDto signalDto = this.convertSignalDto(signalInfo);
            // 初期状態にはデフォルト値を設定とする。
            signalDto.setSignalValue(DEFAULT_SIGNAL_STATE);
            definitionDtoList.add(signalDto);
        }

        sendGetAllStateRequest(signalList);
        return definitionDtoList;
    }

    /**
     * シグナルの全状態を取得するリクエストを生成する。
     * 
     * @param signalList
     *            シグナル定義情報一覧
     */
    private void sendGetAllStateRequest(final List<SignalInfo> signalList)
    {
        ConnectionClient connectionClient = ConnectionClient.getInstance();
        List<CommunicationClient> clientList = connectionClient.getClientList();
        for (CommunicationClient communicationClient : clientList)
        {
            Telegram telegram = createAllStateTelegram(signalList);
            communicationClient.sendTelegram(telegram);
        }

    }

    /**
     * 閾値判定定義情報を更新するリクエストを生成する。
     * 
     * @param signalDefinitionDto 閾値判定定義情報
     */
    private void addSignalDefinitionRequest(final SignalDefinitionDto signalDefinitionDto)
    {
        ConnectionClient connectionClient = ConnectionClient.getInstance();
        List<CommunicationClient> clientList = connectionClient.getClientList();
        for (CommunicationClient communicationClient : clientList)
        {
            Telegram telegram = createAddSignalDefinition(signalDefinitionDto);
            communicationClient.sendTelegram(telegram);
        }

    }

    /**
     * 全閾値情報を取得する電文を作成する。
     * 
     * @param signalList
     *            シグナル定義情報のリスト
     * @return 全閾値情報を取得する電文
     */
    private Telegram createAllStateTelegram(final List<SignalInfo> signalList)
    {
        Telegram telegram = new Telegram();

        Header requestHeader = new Header();
        requestHeader.setByteTelegramKind(TelegramConstants.BYTE_TELEGRAM_KIND_SIGNAL_STATE);
        requestHeader.setByteRequestKind(TelegramConstants.BYTE_REQUEST_KIND_REQUEST);

        int dtoCount = signalList.size();

        // 閾値判定定義情報
        Body signalBody = new Body();

        signalBody.setStrObjName(TelegramConstants.OBJECTNAME_RESOURCEALARM);
        signalBody.setStrItemName(TelegramConstants.ITEMNAME_ALARM_ID);
        signalBody.setByteItemMode(ItemType.ITEMTYPE_STRING);
        signalBody.setIntLoopCount(dtoCount);
        String[] signalNames = new String[dtoCount];
        for (int cnt = 0; cnt < dtoCount; cnt++)
        {
            SignalInfo signalInfo = signalList.get(cnt);
            signalNames[cnt] = signalInfo.signalName;
        }
        signalBody.setObjItemValueArr(signalNames);

        Body[] requestBodys = { signalBody };

        telegram.setObjHeader(requestHeader);
        telegram.setObjBody(requestBodys);

        return telegram;
    }

    /**
     * 閾値判定定義を取得する電文を作成する。
     * 
     * @param signalDefinitionDto シグナル定義情報のリスト
     * @return 全閾値情報を取得する電文
     */
    private Telegram createAddSignalDefinition(final SignalDefinitionDto signalDefinitionDto)
    {
        Telegram telegram = new Telegram();

        Header requestHeader = new Header();
        requestHeader.setByteTelegramKind(TelegramConstants.BYTE_TELEGRAM_KIND_SIGNAL_DEFINITION);
        requestHeader.setByteRequestKind(TelegramConstants.BYTE_REQUEST_KIND_NOTIFY);

        // 閾値判定定義情報
        Body signalBody = new Body();

        signalBody.setStrObjName(TelegramConstants.OBJECTNAME_SIGNAL_CHANGE);
        signalBody.setStrItemName(TelegramConstants.ITEMNAME_SIGNAL_ADD);
        signalBody.setByteItemMode(ItemType.ITEMTYPE_STRING);

        int signalId = signalDefinitionDto.getSignalId();
        String signalName = signalDefinitionDto.getSignalName();
        String matchingPattern = signalDefinitionDto.getMatchingPattern();
        int level = signalDefinitionDto.getLevel();
        double escalationPeriod = signalDefinitionDto.getEscalationPeriod();
        String patternValue = signalDefinitionDto.getPatternValue();

        int dtoCount = 6;
        signalBody.setIntLoopCount(dtoCount);
        String[] signalDefObj =
                { String.valueOf(signalId), signalName, matchingPattern, String.valueOf(level),
                        String.valueOf(escalationPeriod), patternValue };
        signalBody.setObjItemValueArr(signalDefObj);

        Body[] requestBodys = { signalBody };

        telegram.setObjHeader(requestHeader);
        telegram.setObjBody(requestBodys);

        return telegram;
    }

    /**
     * シグナル定義をDBに登録する。
     * 
     * @param signalInfo
     *            シグナル定義
     * @return シグナル定義のDTOオブジェクト
     */
    @Transactional
    public SignalDefinitionDto insertSignalInfo(final SignalInfo signalInfo)
    {
        int signalId = 0;
        try
        {
            signalInfoDao.insert(signalInfo);
            signalId = signalInfoDao.selectSequenceNum(signalInfo);
        }
        catch (DuplicateKeyException dkEx)
        {
            LOGGER.log(LogMessageCodes.SQL_EXCEPTION, dkEx, dkEx.getMessage());
            return new SignalDefinitionDto();
        }

        SignalDefinitionDto signalDefinitionDto = this.convertSignalDto(signalInfo);
        signalDefinitionDto.setSignalId(signalId);

        // 初期状態にはデフォルト値を設定とする。
        signalDefinitionDto.setSignalValue(DEFAULT_SIGNAL_STATE);

        addSignalDefinitionRequest(signalDefinitionDto);

        return signalDefinitionDto;
    }

    /**
     * シグナル定義をDBから取得する。
     * @param signalName シグナル名
     * @return シグナル定義のDTOオブジェクト
     */
    public SignalDefinitionDto getSignalInfo(final String signalName)
    {
        SignalInfo signalInfo = signalInfoDao.selectByName(signalName);
        SignalDefinitionDto defitionDto = this.convertSignalDto(signalInfo);
        return defitionDto;
    }

    /**
     * シグナル定義を更新する。
     * 
     * @param signalInfo
     *            シグナル定義
     * @return {@link SignalDefinitionDto}オブジェクト
     */
    public SignalDefinitionDto updateSignalInfo(final SignalInfo signalInfo)
    {
        try
        {
            SignalInfo beforeSignalInfo = signalInfoDao.selectById(signalInfo.signalId);
            if (beforeSignalInfo == null)
            {
                return new SignalDefinitionDto();
            }

            signalInfoDao.update(signalInfo);

            String beforeItemName = beforeSignalInfo.signalName;
            String afterItemName = signalInfo.signalName;

            this.updateMeasurementItemName(beforeItemName, afterItemName);
        }
        catch (PersistenceException pEx)
        {
            Throwable cause = pEx.getCause();
            if (cause instanceof SQLException)
            {
                SQLException sqlEx = (SQLException)cause;
                LOGGER.log(LogMessageCodes.SQL_EXCEPTION, sqlEx, sqlEx.getMessage());
            }
            else
            {
                LOGGER.log(LogMessageCodes.SQL_EXCEPTION, pEx, pEx.getMessage());
            }

            return new SignalDefinitionDto();
        }
        catch (SQLException sqlEx)
        {
            LOGGER.log(LogMessageCodes.SQL_EXCEPTION, sqlEx, sqlEx.getMessage());
            return new SignalDefinitionDto();
        }

        SignalDefinitionDto signalDefinitionDto = this.convertSignalDto(signalInfo);

        signalDefinitionDto.setSignalValue(0);

        return signalDefinitionDto;
    }

    /**
     * シグナル名に該当するシグナル定義をDBから削除する。
     *
     * @param signalName
     *            シグナル名
     */
    public void deleteSignalInfo(final String signalName)
    {
        try
        {
            signalInfoDao.delete(signalName);
            this.deleteMeasurementItem(signalName);
        }
        catch (PersistenceException pEx)
        {
            Throwable cause = pEx.getCause();
            if (cause instanceof SQLException)
            {
                SQLException sqlEx = (SQLException)cause;
                LOGGER.log(LogMessageCodes.SQL_EXCEPTION, sqlEx, sqlEx.getMessage());
            }
            else
            {
                LOGGER.log(LogMessageCodes.SQL_EXCEPTION, pEx, pEx.getMessage());
            }
        }
        catch (SQLException sqlEx)
        {
            LOGGER.log(LogMessageCodes.SQL_EXCEPTION, sqlEx, sqlEx.getMessage());
        }
    }

    /**
     * SignalDefinitionDtoオブジェクトをSignalInfoオブジェクトに変換する。
     * 
     * @param definitionDto
     *            SignalDefinitionDtoオブジェクト
     * 
     * @return SignalInfoオブジェクト
     */
    public SignalInfo convertSignalInfo(final SignalDefinitionDto definitionDto)
    {
        SignalInfo signalInfo = new SignalInfo();

        signalInfo.signalId = definitionDto.getSignalId();
        signalInfo.signalName = definitionDto.getSignalName();
        signalInfo.matchingPattern = definitionDto.getMatchingPattern();
        signalInfo.level = definitionDto.getLevel();
        signalInfo.patternValue = definitionDto.getPatternValue();
        signalInfo.escalationPeriod = definitionDto.getEscalationPeriod();

        return signalInfo;
    }

    /**
     * SignalInfoオブジェクトをSignalDefinitionDtoオブジェクトに変換する。
     * 
     * @param signalInfo
     *            SignalInfoオブジェクト
     * @return SignalDefinitionDtoオブジェクト
     */
    private SignalDefinitionDto convertSignalDto(final SignalInfo signalInfo)
    {

        SignalDefinitionDto definitionDto = new SignalDefinitionDto();

        definitionDto.setSignalId(signalInfo.signalId);
        definitionDto.setSignalName(signalInfo.signalName);
        definitionDto.setMatchingPattern(signalInfo.matchingPattern);
        definitionDto.setLevel(signalInfo.level);
        definitionDto.setPatternValue(signalInfo.patternValue);
        definitionDto.setEscalationPeriod(signalInfo.escalationPeriod);

        return definitionDto;
    }

    /**
     * javelin_measurement_itemテーブルのMEASUREMENT_ITEM_NAMEを更新する。
     * 
     * @param beforeItemName
     *            更新前のMEASUREMENT_ITEM_NAME
     * @param afterItemName
     *            更新前のMEASUREMENT_ITEM_NAME
     * @throws SQLException
     *             SQL 実行時に例外が発生した場合
     */
    private void updateMeasurementItemName(final String beforeItemName, final String afterItemName)
        throws SQLException
    {
        DatabaseManager dbMmanager = DatabaseManager.getInstance();
        String dbName = dbMmanager.getDataBaseName(1);

        JavelinMeasurementItemDao.updateMeasurementItemName(dbName, beforeItemName, afterItemName);
    }

    /**
     * javelin_measurement_itemテーブルの指定したMEASUREMENT_ITEM_NAMEのレコードを削除する。
     * 
     * @param itemName
     *            削除するレコードの MEASUREMENT_ITEM_NAME
     * @throws SQLException
     *             SQL 実行時に例外が発生した場合
     */
    private void deleteMeasurementItem(final String itemName)
        throws SQLException
    {
        DatabaseManager dbMmanager = DatabaseManager.getInstance();
        String dbName = dbMmanager.getDataBaseName(1);

        JavelinMeasurementItemDao.deleteByMeasurementItemId(dbName, itemName);
    }

}
