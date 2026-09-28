package finance.dsf.widget;

import android.content.Context;
import android.graphics.Typeface;

final class WidgetFont {
    private static Typeface regular;
    static synchronized Typeface regular(Context context) {
        if(regular==null)regular=Typeface.create(context.getResources().getFont(R.font.roboto),Typeface.NORMAL);
        return regular;
    }
}
