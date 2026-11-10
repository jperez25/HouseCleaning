package com.occ.housecleaning.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Shared page-parameter handling: out-of-range values are clamped, not rejected. */
final class Paging {

    private Paging() {
    }

    static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MemberLedgerController.MAX_PAGE_SIZE), sort);
    }
}
