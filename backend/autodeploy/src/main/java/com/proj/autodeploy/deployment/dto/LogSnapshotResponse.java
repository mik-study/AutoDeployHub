package com.proj.autodeploy.deployment.dto;

import java.util.List;

/**
 * 배포 로그 스냅샷 응답. (05_api_spec.md §4.6)
 *
 * <p>공통 {@code ApiResponse<T>} 로 감싸지 않는다 — 명세가 {@code data} 와 같은 레벨에
 * {@code nextFromSequence}/{@code hasMore} 를 두기 때문이다. {@code PagedResponse} 와 같은 결.
 *
 * @param data             로그 목록 (sequence 오름차순)
 * @param nextFromSequence 다음 요청에 넣을 fromSequence. 마지막 sequence + 1
 * @param hasMore          더 가져올 게 남았는지
 */
public record LogSnapshotResponse(
        List<LogEntryResponse> data,
        Long nextFromSequence,
        boolean hasMore
) {
}
