package com.ganjunbao.transaction.reconciliation;

import java.time.LocalDate;

public record ReconciliationSummary(
        long batchId,
        LocalDate businessDate,
        int localRecordCount,
        int channelRecordCount,
        int differenceCount
) {
}
