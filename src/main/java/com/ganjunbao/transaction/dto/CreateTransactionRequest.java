package com.ganjunbao.transaction.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateTransactionRequest {
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于等于0.01")
    private BigDecimal amount;

    @NotBlank(message = "交易描述不能为空")
    @Size(max = 200, message = "交易描述不能超过200个字符")
    private String description;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 100, message = "幂等键不能超过100个字符")
    private String idempotencyKey;

    @NotBlank(message = "币种不能为空")
    @Size(min = 3, max = 3, message = "币种必须是3位代码")
    private String currency;

    @NotBlank(message = "通道编码不能为空")
    @Size(max = 50, message = "通道编码不能超过50个字符")
    private String channelCode;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }
}
