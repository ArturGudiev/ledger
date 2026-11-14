package com.ledger.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ledger.dto.CreateWalletRequest;
import com.ledger.dto.LedgerEntryDto;
import com.ledger.dto.PageResponse;
import com.ledger.dto.WalletDto;
import com.ledger.exception.ResourceNotFoundException;
import com.ledger.model.User;
import com.ledger.repository.LedgerEntryRepository;
import com.ledger.service.WalletService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

  private final WalletService walletService;
  private final LedgerEntryRepository ledgerEntryRepository;

  public WalletController(WalletService walletService, LedgerEntryRepository ledgerEntryRepository) {
    this.walletService = walletService;
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  @PostMapping
  public ResponseEntity<WalletDto> create(
      @AuthenticationPrincipal User user,
      @Valid @RequestBody CreateWalletRequest request
  ) {
    return ResponseEntity.ok(walletService.create(requireUser(user).getId(), request));
  }

  @GetMapping
  public ResponseEntity<List<WalletDto>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(walletService.list(requireUser(user).getId()));
  }

  @GetMapping("/{walletId}")
  public ResponseEntity<WalletDto> get(@AuthenticationPrincipal User user, @PathVariable Long walletId) {
    return ResponseEntity.ok(walletService.get(requireUser(user).getId(), walletId));
  }

  @GetMapping("/{walletId}/ledger")
  public ResponseEntity<PageResponse<LedgerEntryDto>> ledger(
      @AuthenticationPrincipal User user,
      @PathVariable Long walletId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    Long userId = requireUser(user).getId();
    walletService.findOwned(userId, walletId);
    int pageNumber = Math.max(page, 0);
    int pageSize = size <= 0 ? 20 : Math.min(size, 100);
    return ResponseEntity.ok(PageResponse.of(
        ledgerEntryRepository.findByWalletIdOrderByCreatedAtDescIdDesc(walletId, PageRequest.of(pageNumber, pageSize)),
        LedgerEntryDto::from
    ));
  }

  private User requireUser(User user) {
    if (user == null || user.getId() == null) {
      throw new ResourceNotFoundException("User not found");
    }
    return user;
  }
}
