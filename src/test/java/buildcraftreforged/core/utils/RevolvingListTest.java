package buildcraftreforged.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class RevolvingListTest {

    @Test
    void rotatesInBothDirections() {
        RevolvingList<String> list = new RevolvingList<>(List.of("a", "b", "c"));
        assertEquals("a", list.getCurrent());
        list.rotateRight();
        assertEquals("b", list.getCurrent());
        list.rotateLeft();
        list.rotateLeft();
        assertEquals("c", list.getCurrent());
    }

    @Test
    void setCurrentRotatesToTheElementAndIgnoresUnknownOnes() {
        RevolvingList<String> list = new RevolvingList<>(List.of("a", "b", "c"));
        list.setCurrent("c");
        assertEquals("c", list.getCurrent());
        list.setCurrent("z");
        assertEquals("c", list.getCurrent());
        assertEquals(3, list.size());
    }

    @Test
    void emptyListHasNoCurrent() {
        RevolvingList<String> list = new RevolvingList<>();
        list.rotateLeft();
        list.rotateRight();
        assertNull(list.getCurrent());
    }
}
