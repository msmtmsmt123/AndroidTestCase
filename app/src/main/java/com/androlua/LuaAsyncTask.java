package com.androlua;

import android.os.AsyncTask;

import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaObject;
import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

public class LuaAsyncTask extends AsyncTask implements LuaGcable {

    private Object[] loadeds;
    private LuaState L;
    private ILuaContext mILuaContext;
    private byte[] mBuffer;
    private long mDelay = 0;
    private LuaObject mCallback;
    private LuaObject mUpdate;

    public LuaAsyncTask(ILuaContext ILuaContext, long delay, LuaObject callback) throws LuaException {
        ILuaContext.regGc(this);
        mILuaContext = ILuaContext;
        mDelay = delay;
        mCallback = callback;
    }

    public LuaAsyncTask(ILuaContext ILuaContext, String src, LuaObject callback) throws LuaException {
        ILuaContext.regGc(this);
        mILuaContext = ILuaContext;
        mBuffer = src.getBytes();
        mCallback = callback;
    }

    public LuaAsyncTask(ILuaContext ILuaContext, LuaObject func, LuaObject callback) throws LuaException {
        ILuaContext.regGc(this);
        mILuaContext = ILuaContext;
        mBuffer = func.dump();
        mCallback = callback;
        LuaState l = func.getLuaState();
        LuaObject g = l.getLuaObject("luajava");
        LuaObject loaded = g.getField("imported");
        if (!loaded.isNil()) {
            loadeds = loaded.asArray();
        }
    }


    public LuaAsyncTask(ILuaContext ILuaContext, LuaObject func, LuaObject update, LuaObject callback) throws LuaException {
        ILuaContext.regGc(this);
        mILuaContext = ILuaContext;
        mBuffer = func.dump();
        mUpdate = update;
        mCallback = callback;
    }

    @Override
    public void gc() {
        // TODO: Implement this method
        if (getStatus() == Status.RUNNING)
            cancel(true);
    }

    public void execute() throws IllegalArgumentException, ArrayIndexOutOfBoundsException, LuaException {
        // TODO: Implement this method
        super.execute();
    }

    public void update(Object msg) {
        publishProgress(msg);
    }

    public void update(String msg) {
        publishProgress(msg);
    }

    public void update(int msg) {
        publishProgress(msg);
    }

    @Override
    protected Object doInBackground(Object[] args) {
        if (mDelay != 0) {
            try {
                Thread.sleep(mDelay);
            } catch (InterruptedException e) {
            }
            return args;
        }
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

        L.getGlobal("luajava");
        L.pushString(mILuaContext.getLuaDir());
        L.setField(-2, "luadir");
        L.pop(1);

        try {
            JavaFunction print = new LuaPrint(null, L); //mILuaContext
            print.register("print");

            JavaFunction update = new JavaFunction(L) {

                @Override
                public int execute() throws LuaException {
                    // TODO: Implement this method
                    update(L.toJavaObject(2));
                    return 0;
                }
            };

            update.register("update");

            L.getGlobal("package");

            L.pushString(mILuaContext.getLuaLpath());
            L.setField(-2, "path");
            L.pushString(mILuaContext.getLuaCpath());
            L.setField(-2, "cpath");
            L.pop(1);
        } catch (LuaException e) {
            mILuaContext.sendError("AsyncTask", e);
        }

        if (loadeds != null) {
            LuaObject require = L.getLuaObject("require");
            try {
                require.call("import");
                LuaObject _import = L.getLuaObject("import");
                for (Object s : loadeds)
                    _import.call(s.toString());
            } catch (LuaException e) {

            }
        }

        try {
            L.setTop(0);
            int ok = L.LloadBuffer(mBuffer, "LuaAsyncTask");

            if (ok == 0) {
                L.getGlobal("debug");
                L.getField(-1, "traceback");
                L.remove(-2);
                L.insert(-2);
                int l = args.length;
                for (int i = 0; i < l; i++) {
                    L.pushObjectValue(args[i]);
                }
                ok = L.pcall(l, LuaState.LUA_MULTRET, -2 - l);
                if (ok == 0) {
                    int n = L.getTop() - 1;
                    Object[] ret = new Object[n];
                    for (int i = 0; i < n; i++)
                        ret[i] = L.toJavaObject(i + 2);
                    return ret;
                }
            }
            throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
        } catch (LuaException e) {
            mILuaContext.sendError("doInBackground", e);
        }


        return null;
    }

    @Override
    protected void onPostExecute(Object result) {
        // TODO: Implement this method

        if (isCancelled())
            return;
        try {
            if (mCallback != null)
                mCallback.call((Object[]) result);
        } catch (LuaException e) {
            mILuaContext.sendError("onPostExecute", e);
        }
        super.onPostExecute(result);
        if (L != null)
            L.gc(LuaState.LUA_GCCOLLECT, 1);
        System.gc();
        //L.close();
    }

    @Override
    protected void onProgressUpdate(Object[] values) {
        // TODO: Implement this method
        try {
            if (mUpdate != null)
                mUpdate.call(values);
        } catch (LuaException e) {
            mILuaContext.sendError("onProgressUpdate", e);
        }
        super.onProgressUpdate(values);
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

}

