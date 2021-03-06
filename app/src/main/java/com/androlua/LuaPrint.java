package com.androlua;

import com.bigsing.util.Utils;
import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaState;

public class LuaPrint extends JavaFunction {

    private LuaState L;
    private ILuaPrintListener mPrintListener = null;
    private StringBuilder output = new StringBuilder();

    public LuaPrint(ILuaPrintListener printListener, LuaState L) {
        super(L);
        this.L = L;
        mPrintListener = printListener;
    }

    @Override
    public int execute() throws LuaException {
        if (L.getTop() < 2) {
            this.print("");
            return 0;
        }

        for (int i = 2; i <= L.getTop(); i++) {
            int type = L.type(i);
            String val = null;
            String stype = L.typeName(type);
            if (stype.equals("userdata")) {
                Object obj = L.toJavaObject(i);
                if (obj != null) {
                    val = obj.toString();
                }
            } else if (stype.equals("boolean")) {
                val = L.toBoolean(i) ? "true" : "false";
            } else {
                val = L.toString(i);
            }
            if (val == null) {
                val = stype;
            }
            output.append(val);
            output.append("\t");
        }
        this.print(output.toString());
        output.setLength(0);
        return 0;
    }

    private void print(String msg) {
        if (mPrintListener != null) {
            mPrintListener.onPrint("");
        } else {
            Utils.logd(msg);
        }
    }


}

