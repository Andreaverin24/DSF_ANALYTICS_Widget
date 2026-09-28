package finance.dsf.widget;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RollingDigitsTest {
    @Test public void unchangedAndMissingValuesDoNotAnimate() {
        for(String previous:new String[]{null,"—","9,61%"})
            assertTrue(RollingDigits.cells(previous,"9,61%",true).stream().allMatch(c->c.direction()==0));
        assertTrue(RollingDigits.cells("$100","$101",false).stream().allMatch(c->c.direction()==0));
    }
    @Test public void carryUsesDecimalPositionsAndLeavesPunctuationStill() {
        List<RollingDigits.Cell> cells=RollingDigits.cells("$999","$1\u00a0000",true);
        assertEquals("$1\u00a0000",cells.stream().map(RollingDigits.Cell::after).reduce("",String::concat));
        assertEquals(3,cells.stream().filter(c->c.direction()==1).count());
        assertTrue(cells.stream().filter(c->!c.after().matches("[0-9]")).allMatch(c->c.direction()==0));
        assertEquals(3,RollingDigits.cells("9,99%","10,00%",true).stream().filter(c->c.direction()==1).count());
    }
    @Test public void decreasesNegativeAndZeroRemainRealValues() {
        assertEquals(-1,RollingDigits.cells("9,61%","9,60%",true).stream().filter(c->c.direction()!=0).findFirst().get().direction());
        assertTrue(RollingDigits.cells("-1,00%","-2,00%",true).stream().anyMatch(c->c.direction()==-1));
        assertTrue(RollingDigits.cells("-1,00%","0,00%",true).stream().anyMatch(c->c.direction()==1));
        assertTrue(RollingDigits.cells("1,00%","—",true).stream().allMatch(c->c.direction()==0));
    }
}
