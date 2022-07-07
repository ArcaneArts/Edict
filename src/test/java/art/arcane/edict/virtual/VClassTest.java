package art.arcane.edict.virtual;

import art.arcane.edict.Edict;
import art.arcane.edict.testconstruct.TestCommandClass;
import art.arcane.edict.testconstruct.TestUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VClassTest {

    VClass SUT = VClass.fromInstance(new TestCommandClass(), null, new Edict());

    @Test
    void fromClass() {
        assertNotNull(SUT);
        assertEquals("test", SUT.name());
        List<String> childrenNames = SUT.children().stream().map(VCommandable::name).toList();
        assertTrue(childrenNames.contains("command"));
        assertTrue(childrenNames.contains("subcategory"));
        assertFalse(childrenNames.contains("empty"));
        assertFalse(childrenNames.contains("TestNotCommandClass"));
    }

    @Test
    void sortAndFilterChildren() {
        List<String> commandableBracket = VClass.sortAndFilterChildren(SUT.children(), "[", new TestUser(), SUT.system().settings().matchThreshold).stream().map(VCommandable::name).toList();
        List<String> commandableMatch = VClass.sortAndFilterChildren(SUT.children(), "command", new TestUser(), SUT.system().settings().matchThreshold).stream().map(VCommandable::name).toList();
        assertTrue(commandableBracket.isEmpty());
        assertEquals("command", commandableMatch.get(0));
        assertEquals(1, commandableMatch.size());
    }

    @Test
    void name() {
        assertEquals("test", SUT.name());
    }

    @Test
    void aliases() {
        assertTrue(List.of(SUT.aliases()).contains("alias"));
    }

    @Test
    void run() {
        // Ran from EdictTest
    }
}