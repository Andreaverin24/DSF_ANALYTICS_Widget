package finance.dsf.widget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Presentation only: source values and the snapshot are never interpolated. */
final class RollingDigits {
    record Cell(String before,String after,int direction) {}
    static List<Cell> cells(String previous,String current,boolean animate) {
        BigDecimal before=number(previous),after=number(current);
        int direction=animate && before!=null && after!=null?after.compareTo(before):0;
        String oldDigits=previous==null?"":previous.replaceAll("[^0-9]","");
        int remaining=current.replaceAll("[^0-9]","").length();
        List<Cell> result=new ArrayList<>();
        for(char character:current.toCharArray()) {
            String next=String.valueOf(character),old=next;
            if(character>='0' && character<='9') {
                int index=oldDigits.length()-remaining--;
                if(direction!=0 && index>=0)old=String.valueOf(oldDigits.charAt(index));
            }
            result.add(new Cell(old,next,old.equals(next)?0:direction));
        }
        return result;
    }
    private static BigDecimal number(String formatted) {
        if(formatted==null)return null;
        try {return new BigDecimal(formatted.replaceAll("[$%\\s\\u00a0\\u202f]","").replace(',','.'));}
        catch(NumberFormatException ignored){return null;}
    }
}
