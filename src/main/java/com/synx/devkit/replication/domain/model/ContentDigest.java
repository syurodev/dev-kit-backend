package com.synx.devkit.replication.domain.model;

import java.util.Arrays;

public record ContentDigest(byte[] bytes) {
    public ContentDigest {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public boolean sameAs(ContentDigest other) {
        return other != null && Arrays.equals(bytes, other.bytes);
    }
}
