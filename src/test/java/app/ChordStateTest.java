package app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ChordStateTest {

    private static final int CHORD_LEVEL = 6;

    private Map<ServentInfo, ChordState> states;

    private static List<ServentInfo> nodesWithDistinctIds(int count) {
        List<ServentInfo> nodes = new ArrayList<>();
        Set<Integer> usedIds = new HashSet<>();
        for (int port = 1000; nodes.size() < count; port++) {
            ServentInfo node = new ServentInfo("localhost", port);
            if (usedIds.add(node.getChordId())) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static Map<ServentInfo, ChordState> buildChord(List<ServentInfo> nodes) {
        Map<ServentInfo, ChordState> chord = new LinkedHashMap<>();
        for (ServentInfo node : nodes) {
            AppConfig.myServentInfo = node;
            ChordState state = new ChordState();
            List<ServentInfo> others = new ArrayList<>(nodes);
            others.remove(node);
            state.addNodes(others);
            chord.put(node, state);
        }
        return chord;
    }

    @BeforeEach
    void setUp() {
        ChordState.CHORD_SIZE = 64;
        states = buildChord(nodesWithDistinctIds(16));
    }

    private ServentInfo nodeWithId(int chordId) {
        return states.keySet().stream().filter(node -> node.getChordId() == chordId).findFirst().orElseThrow();
    }

    private int hops(ServentInfo from, ServentInfo to) {
        ServentInfo current = from;
        for (int hops = 0; hops <= states.size(); hops++) {
            if (current.getChordId() == to.getChordId()) {
                return hops;
            }
            AppConfig.myServentInfo = current;
            current = nodeWithId(states.get(current).getNextNodeForKey(to.getChordId()).getChordId());
        }
        return -1;
    }

    @Test
    void everyKeyBelongsToExactlyOneNode() {
        for (int key = 0; key < ChordState.CHORD_SIZE; key++) {
            int owners = 0;
            for (Map.Entry<ServentInfo, ChordState> entry : states.entrySet()) {
                AppConfig.myServentInfo = entry.getKey();
                if (entry.getValue().isKeyMine(key)) {
                    owners++;
                }
            }
            assertEquals(1, owners, "owners of key " + key);
        }
    }

    @Test
    void messagesReachAnyNodeInLogarithmicNumberOfHops() {
        for (ServentInfo from : states.keySet()) {
            for (ServentInfo to : states.keySet()) {
                int hops = hops(from, to);
                assertTrue(hops >= 0, "no route from " + from + " to " + to);
                assertTrue(hops <= CHORD_LEVEL, hops + " hops from " + from + " to " + to);
            }
        }
    }

    @Test
    void successorAndPredecessorFormOneRing() {
        ServentInfo start = states.keySet().iterator().next();
        ServentInfo current = start;
        Set<Integer> visited = new HashSet<>();
        do {
            assertTrue(visited.add(current.getChordId()), "visited twice: " + current);
            ServentInfo next = nodeWithId(states.get(current).getSuccessor().getChordId());
            assertEquals(current.getChordId(), states.get(next).getPredecessor().getChordId());
            current = next;
        } while (current != start);
        assertEquals(states.size(), visited.size());
    }

    @Test
    void routingStillWorksAfterNodesAreRemoved() {
        List<ServentInfo> nodes = new ArrayList<>(states.keySet());
        List<ServentInfo> removed = List.of(nodes.get(3), nodes.get(4), nodes.get(9));

        for (ServentInfo gone : removed) {
            states.remove(gone);
            for (Map.Entry<ServentInfo, ChordState> entry : states.entrySet()) {
                AppConfig.myServentInfo = entry.getKey();
                assertTrue(entry.getValue().removeNode(gone.getChordId()));
                assertFalse(entry.getValue().isMember(gone.getChordId()));
            }
        }

        everyKeyBelongsToExactlyOneNode();
        messagesReachAnyNodeInLogarithmicNumberOfHops();
        successorAndPredecessorFormOneRing();
    }

    @Test
    void lastNodeIsAloneAndOwnsEverything() {
        List<ServentInfo> nodes = nodesWithDistinctIds(2);
        AppConfig.myServentInfo = nodes.get(0);
        ChordState state = new ChordState();
        state.addNodes(List.of(nodes.get(1)));
        assertEquals(nodes.get(1).getChordId(), state.getSuccessor().getChordId());

        assertTrue(state.removeNode(nodes.get(1).getChordId()));
        assertFalse(state.removeNode(nodes.get(1).getChordId()));

        assertNull(state.getSuccessor());
        assertNull(state.getPredecessor());
        for (int key = 0; key < ChordState.CHORD_SIZE; key++) {
            assertTrue(state.isKeyMine(key));
        }
    }

    @Test
    void knownNodesAndOurselvesAreNotAddedAgain() {
        ServentInfo me = states.keySet().iterator().next();
        AppConfig.myServentInfo = me;
        ChordState state = states.get(me);

        state.addNodes(new ArrayList<>(states.keySet()));

        assertEquals(states.size() - 1, state.getAllNodes().size());
    }
}
