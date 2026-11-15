package com.ganjunbao.transaction.controller;

import com.ganjunbao.transaction.dto.CreateTransactionRequest;
import com.ganjunbao.transaction.model.Transaction;
import com.ganjunbao.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(value = "X-User-Id", required = false)
            String userId
    ) {
        return transactionService.create(request, userId);
    }

    @GetMapping("/{id}")
    public Transaction getById(@PathVariable String id) {
        return transactionService.getById(id);
    }

    @PostMapping("/{id}/confirm")
    public Transaction confirm(@PathVariable String id) {
        return transactionService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public Transaction cancel(@PathVariable String id) {
        return transactionService.cancel(id);
    }

    @PostMapping("/{id}/processing")
    public Transaction startProcessing(@PathVariable String id) {
        return transactionService.startProcessing(id);
    }

    @PostMapping("/{id}/succeed")
    public Transaction succeed(@PathVariable String id) {
        return transactionService.succeed(id);
    }

    @PostMapping("/{id}/fail")
    public Transaction fail(@PathVariable String id) {
        return transactionService.fail(id);
    }
}
