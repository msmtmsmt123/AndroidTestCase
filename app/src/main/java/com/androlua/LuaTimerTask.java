package com.androlua;

import com.androlua.util.TimerTaskX;
import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaObject;
import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

import java.io.IOException;
import java.util.regex.Pattern;

public class LuaTimerTask extends TimerTaskX {
    private LuaState L;

    private ILuaContext mILuaContext;

    private String mSrc;

    private Object[] mArg = new Object[0];

    private boolean mEnabled = true;

    private byte[] mBuffer;

    public LuaTimerTask(ILuaContext ILuaContext, String src) throws LuaException {
        this(ILuaContext, src, null);
    }

    public LuaTimerTask(ILuaContext ILuaContext, String src, Object[] arg) throws LuaException {
        mILuaContext = ILuaContext;
        mSrc = src;
        if (arg != null)
            mArg = arg;
    }

    public LuaTimerTask(ILuaContext ILuaContext, LuaObject func) throws LuaException {
        this(ILuaContext, func, null);
    }

    public LuaTimerTask(ILuaContext ILuaContext, LuaObject func, Object[] arg) throws LuaException {

        mILuaContext = ILuaContext;
        if (arg != null)
            mArg = arg;

        mBuffer = func.dump();
    }


    @Override
    public void run() {
        if (mEnabled == false)
            return;
        try {
            if (L == null) {
                initLua();

                if (mBuffer != null)
                    newLuaThread(mBuffer, mArg);
                else
                    newLuaThread(mSrc, mArg);
            } else {
                L.getGlobal("run");
                if (!L.isNil(-1))
                    runFunc("run");
                else {
                    if (mBuffer != null)
                        newLuaThread(mBuffer, mArg);
                    else
                        newLuaThread(mSrc, mArg);
                }
            }
        } catch (LuaException e) {
            mILuaContext.onPrint(e.getMessage());
        }
        L.gc(LuaState.LUA_GCCOLLECT, 1);
        System.gc();

    }

    @Override
    public boolean cancel() {
        // TODO: Implement this method
        return super.cancel();
    }

    public void setArg(Object[] arg) {
        mArg = arg;
    }

    public void setArg(LuaObject arg) throws ArrayIndexOutOfBoundsException, LuaException, IllegalArgumentException {
        mArg = arg.asArray();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void set(String key, Object value) throws LuaException {
        L.pushObjectValue(value);
        L.setGlobal(key);
    }

    public Object get(String key) throws LuaException {
        L.getGlobal(key);
        return L.toJavaObject(-1);
    }

    private String errorReason(int error) {
        switch (error) {
            case 6:
                return "error error";
            case 5:
                return "GC error";
            case 4:
                return "Out of memory";
            case 3:
                return "Syntax error";
            case 2:
                return "Runtime error";
            case 1:
                return "Yield error";
        }
        return "Unknown error " + error;
    }

    private void initLua() throws LuaException {
        L = LuaStateFactory.newLuaState();
        L.openLibs();
        L.pushJavaObject(mILuaContext);
        if (mILuaContext instanceof LuaActivity) {
            L.setGlobal("activity");
        } else if (mILuaContext instanceof LuaService) {
            L.setGlobal("service");
        }
        L.pushJavaObject(this);
        L.setGlobal("this");

        L.pushContext(mILuaContext.getContext());

        JavaFunction print = new LuaPrint(mILuaContext, L);
        print.register("print");

        L.getGlobal("package");

        L.pushString(mILuaContext.getLuaLpath());
        L.setField(-2, "path");
        L.pushString(mILuaContext.getLuaCpath());
        L.setField(-2, "cpath");
        L.pop(1);

        JavaFunction set = new JavaFunction(L) {
            @Override
            public int execute() throws LuaException {

                mILuaContext.set(L.toString(2), L.toJavaObject(3));
                return 0;
            }
        };
        set.register("set");

        JavaFunction call = new JavaFunction(L) {
            @Override
            public int execute() throws LuaException {

                int top = L.getTop();
                if (top > 2) {
                    Object[] args = new Object[top - 2];
                    for (int i = 3; i <= top; i++) {
                        args[i - 3] = L.toJavaObject(i);
                    }
                    mILuaContext.call(L.toString(2), args);
                } else if (top == 2) {
                    mILuaContext.call(L.toString(2));
                }
                return 0;
            }
        };
        call.register("call");
    }

    private void newLuaThread(String str, Object... args) {
        try {

            if (Pattern.matches("^\\w+$", str)) {
                doAsset(str + ".lua", args);
            } else if (Pattern.matches("^[\\w\\.\\_/]+$", str)) {
                L.getGlobal("luajava");
                L.pushString(mILuaContext.getLuaDir());
                L.setField(-2, "luadir");
                L.pushString(str);
                L.setField(-2, "luapath");
                L.pop(1);

                doFile(str, args);
            } else {
                doString(str, args);
            }

        } catch (Exception e) {
            mILuaContext.onPrint(this.toString() + " " + e.getMessage());

        }

    }

    private void newLuaThread(byte[] buf, Object... args) throws LuaException {
        int ok = 0;
        L.setTop(0);
        ok = L.LloadBuffer(buf, "TimerTask");

        if (ok == 0) {
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);
            int l = args.length;
            for (int i = 0; i < l; i++) {
                L.pushObjectValue(args[i]);
            }
            ok = L.pcall(l, 0, -2 - l);
            if (ok == 0) {
                return;
            }
        }
        throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    }

    private void doFile(String filePath, Object... args) throws LuaException {
        int ok = 0;
        L.setTop(0);
        ok = L.LloadFile(filePath);

        if (ok == 0) {
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);
            int l = args.length;
            for (int i = 0; i < l; i++) {
                L.pushObjectValue(args[i]);
            }
            ok = L.pcall(l, 0, -2 - l);
            if (ok == 0) {
                return;
            }
        }
        throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    }

    public void doAsset(String name, Object... args) throws LuaException, IOException {
        int ok = 0;
        byte[] bytes = LuaUtil.readAsset(mILuaContext.getContext(), name);
        L.setTop(0);
        ok = L.LloadBuffer(bytes, name);

        if (ok == 0) {
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);
            int l = args.length;
            for (int i = 0; i < l; i++) {
                L.pushObjectValue(args[i]);
            }
            ok = L.pcall(l, 0, -2 - l);
            if (ok == 0) {
                return;
            }
        }
        throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    }

    private void doString(String src, Object... args) throws LuaException {
        L.setTop(0);
        int ok = L.LloadString(src);

        if (ok == 0) {
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);
            int l = args.length;
            for (int i = 0; i < l; i++) {
                L.pushObjectValue(args[i]);
            }
            ok = L.pcall(l, 0, -2 - l);
            if (ok == 0) {

                return;
            }
        }
        throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    }


    private void runFunc(String funcName, Object... args) {
        try {
            L.setTop(0);
            L.getGlobal(funcName);
            if (L.isFunction(-1)) {
                L.getGlobal("debug");
                L.getField(-1, "traceback");
                L.remove(-2);
                L.insert(-2);

                int l = args.length;
                for (int i = 0; i < l; i++) {
                    L.pushObjectValue(args[i]);
                }

                int ok = L.pcall(l, 1, -2 - l);
                if (ok == 0) {
                    return;
                }
                throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
            }
        } catch (LuaException e) {
            mILuaContext.onPrint(funcName + " " + e.getMessage());
        }

    }

    private void setField(String key, Object value) {
        try {
            L.pushObjectValue(value);
            L.setGlobal(key);
        } catch (LuaException e) {
            mILuaContext.onPrint(e.getMessage());
        }
    }

};
