package com.occ.housecleaning.config;

import com.occ.housecleaning.model.Member;
import com.occ.housecleaning.model.MemberLedgerBalance;
import com.occ.housecleaning.repository.MemberLedgerBalanceRepository;
import com.occ.housecleaning.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Seeds the simulated clearing-member registry (and a zeroed ledger balance per member) at startup.
 * Every member is guaranteed a balance row, so novation never has to create one concurrently.
 */
@Component
public class MemberSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MemberSeeder.class);

    public static final String LOW_CREDIT_MEMBER_ID = "MEM-LOWCREDIT";
    public static final String INACTIVE_MEMBER_ID = "MEM-INACTIVE";
    private static final BigDecimal STANDARD_CREDIT_LIMIT = new BigDecimal("1000000000");

    private static final List<String> STANDARD_MEMBER_IDS = List.of(
            "MEM-101", "MEM-102", "MEM-201", "MEM-202", "MEM-301",
            "MEM-302", "MEM-401", "MEM-402", "MEM-501", "MEM-502");

    private final MemberRepository memberRepository;
    private final MemberLedgerBalanceRepository balanceRepository;

    public MemberSeeder(MemberRepository memberRepository, MemberLedgerBalanceRepository balanceRepository) {
        this.memberRepository = memberRepository;
        this.balanceRepository = balanceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        STANDARD_MEMBER_IDS.forEach(id -> seed(id, true, STANDARD_CREDIT_LIMIT));
        seed(LOW_CREDIT_MEMBER_ID, true, BigDecimal.ZERO);
        seed(INACTIVE_MEMBER_ID, false, STANDARD_CREDIT_LIMIT);
        log.info("Clearing member registry ready ({} members)", memberRepository.count());
    }

    private void seed(String memberId, boolean active, BigDecimal creditLimit) {
        if (!memberRepository.existsById(memberId)) {
            memberRepository.save(Member.builder()
                    .memberId(memberId).name("Clearing Member " + memberId)
                    .active(active).creditLimit(creditLimit).build());
        }
        if (!balanceRepository.existsById(memberId)) {
            balanceRepository.save(MemberLedgerBalance.builder()
                    .memberId(memberId).cashBalance(BigDecimal.ZERO)
                    .marginBalance(BigDecimal.ZERO).updatedAt(Instant.now()).build());
        }
    }
}
