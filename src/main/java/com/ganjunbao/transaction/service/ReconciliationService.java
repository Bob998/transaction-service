package com.ganjunbao.transaction.service;

import com.ganjunbao.transaction.mapper.ReconciliationMapper;
import com.ganjunbao.transaction.reconciliation.ReconciliationCandidate;
import com.ganjunbao.transaction.reconciliation.ReconciliationDifference;
import com.ganjunbao.transaction.reconciliation.ReconciliationSummary;
import com.ganjunbao.transaction.reconciliation.DifferenceResolution;
import com.ganjunbao.transaction.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationService {

    private final ReconciliationMapper reconciliationMapper;
    private final AuditLogRepository auditLogRepository;

    public ReconciliationService(
            ReconciliationMapper reconciliationMapper,
            AuditLogRepository auditLogRepository
    ) {
        this.reconciliationMapper = reconciliationMapper;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void resolveDifference(
            long differenceId,
            DifferenceResolution resolution,
            String operator,
            String note
    ) {
        ReconciliationDifference difference = reconciliationMapper
                .findDifferenceById(differenceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "对账差异不存在: " + differenceId
                ));

        int updatedRows = reconciliationMapper.resolveDifference(
                differenceId,
                resolution.name(),
                operator,
                note
        );
        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "对账差异已被处理: " + differenceId
            );
        }

        auditLogRepository.recordAction(
                difference.getTransactionId(),
                "RECONCILIATION_DIFFERENCE_" + resolution.name(),
                operator,
                "{\"differenceId\":" + differenceId
                        + ",\"status\":\"OPEN\"}",
                "{\"differenceId\":" + differenceId
                        + ",\"status\":\"" + resolution.name() + "\"}"
        );
    }

    @Transactional
    public ReconciliationSummary reconcile(
            LocalDate businessDate,
            Map<String, BigDecimal> channelStatement
    ) {
        long batchId = reconciliationMapper.createBatch(businessDate);
        List<ReconciliationCandidate> localRecords =
                reconciliationMapper.findSuccessfulTransactions(businessDate);
        Map<String, BigDecimal> remainingChannelRecords =
                new HashMap<>(channelStatement);
        int differenceCount = 0;

        for (ReconciliationCandidate localRecord : localRecords) {
            BigDecimal channelAmount = remainingChannelRecords.remove(
                    localRecord.getTransactionNo()
            );

            if (channelAmount == null) {
                recordDifference(
                        batchId,
                        localRecord.getTransactionNo(),
                        "CHANNEL_MISSING",
                        localRecord.getAmount(),
                        null
                );
                differenceCount++;
            } else if (localRecord.getAmount().compareTo(channelAmount) != 0) {
                recordDifference(
                        batchId,
                        localRecord.getTransactionNo(),
                        "AMOUNT_MISMATCH",
                        localRecord.getAmount(),
                        channelAmount
                );
                differenceCount++;
            }
        }

        for (Map.Entry<String, BigDecimal> channelRecord
                : remainingChannelRecords.entrySet()) {
            recordDifference(
                    batchId,
                    channelRecord.getKey(),
                    "LOCAL_MISSING",
                    null,
                    channelRecord.getValue()
            );
            differenceCount++;
        }

        reconciliationMapper.completeBatch(
                batchId,
                localRecords.size(),
                channelStatement.size(),
                differenceCount
        );

        return new ReconciliationSummary(
                batchId,
                businessDate,
                localRecords.size(),
                channelStatement.size(),
                differenceCount
        );
    }

    private void recordDifference(
            long batchId,
            String transactionNo,
            String differenceType,
            BigDecimal localAmount,
            BigDecimal channelAmount
    ) {
        reconciliationMapper.insertDifference(
                batchId,
                transactionNo,
                differenceType,
                localAmount,
                channelAmount
        );
    }
}
