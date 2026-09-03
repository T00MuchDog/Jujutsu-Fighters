package com.jjktbf.model.move;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRepositoryTest {

    @Test
    void sparseMoveIdsRemainStableAndNewIdsFollowTheMaximum() {
        MoveRepository repository = new MoveRepository("unused");
        MoveData first = move("000000");
        MoveData third = move("000002");
        repository.add(first);
        repository.add(third);

        assertEquals("000003", repository.nextId());
        assertTrue(repository.delete("000000"));
        assertFalse(repository.exists("000000"));
        assertTrue(repository.exists("000002"));
        assertEquals("000003", repository.nextId());

        MoveData appended = move(null);
        repository.add(appended);
        assertEquals("000003", appended.id);
    }

    private static MoveData move(String id) {
        MoveData move = new MoveData();
        move.id = id;
        return move;
    }
}
