package com.ganjunbao.transaction.model;

import java.time.OffsetDateTime;

public class TransactionAttempt {

    private Long id;
    private Long transactionId;
    private Long channelId;
    private Integer attemptNo;
    private TransactionAttemptStatus status;
    private String requestData;
    private String responseData;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    public TransactionAttempt() {
    }

    public TransactionAttempt(
            Long transactionId,
            Long channelId,
            Integer attemptNo,
            TransactionAttemptStatus status,
            String requestData,
            String responseData,
            String errorCode,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        this.transactionId = transactionId;
        this.channelId = channelId;
        this.attemptNo = attemptNo;
        this.status = status;
        this.requestData = requestData;
        this.responseData = responseData;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getChannelId() {
        return channelId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public TransactionAttemptStatus getStatus() {
        return status;
    }

    public String getRequestData() {
        return requestData;
    }

    public String getResponseData() {
        return responseData;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}