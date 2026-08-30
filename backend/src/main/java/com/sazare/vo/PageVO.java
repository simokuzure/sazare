package com.sazare.vo;

import java.util.List;

public record PageVO<T>(
        List<T> items,
        int page,
        int size,
        long total
) {
}
