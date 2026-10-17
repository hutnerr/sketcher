package com.sketcher.practice;

import com.sketcher.model.Segment;

import java.util.List;

public record ResolvedSegment(Segment segment, List<Integer> imageIds) {
}
