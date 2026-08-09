package com.synx.devkit.replication.adapter.in.web;

import java.util.List;

public record PushRequest(List<PushOperationDto> operations) {
}
