package com.sketcher.practice;

import com.sketcher.filter.Filter;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.TickBehavior;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionSpecCodecTest {

    @Test
    void roundTripsASingleFilterSegment() {
        Segment segment = Segment.filterSegment(
                new Filter.Not(new Filter.TagPredicate(3, true)), 20, OrderMode.SEQUENTIAL, 45,
                TickBehavior.WAIT_FOR_CLICK, 0);
        SessionSpec original = SessionSpec.single(segment);

        SessionSpec decoded = SessionSpecCodec.decode(SessionSpecCodec.encode(original));

        assertEquals(1, decoded.segments().size());
        assertNull(decoded.presetId(), "quick-start sessions are not tied back to a preset id");
        Segment roundTripped = decoded.segments().get(0);
        assertEquals(segment.filter(), roundTripped.filter());
        assertEquals(segment.itemCount(), roundTripped.itemCount());
        assertEquals(segment.orderMode(), roundTripped.orderMode());
        assertEquals(segment.secondsPerImage(), roundTripped.secondsPerImage());
        assertEquals(segment.tickBehavior(), roundTripped.tickBehavior());
    }
}
