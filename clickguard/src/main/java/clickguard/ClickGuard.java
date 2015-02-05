/*
 * Copyright 2015 Feng Dai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package clickguard;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;

import java.lang.reflect.Field;

public abstract class ClickGuard {

    /**
     * Default guard period in millis.
     */
    public static final long DEFAULT_GUARD_PERIOD_MILLIS = 1000L;

    private ClickGuard() {
        // private
    }

    /**
     * Create a ClickGuard with default guard period: {@link #DEFAULT_GUARD_PERIOD_MILLIS}.
     *
     * @return The ClickGuard.
     */
    public static ClickGuard newGuard() {
        return newGuard(DEFAULT_GUARD_PERIOD_MILLIS);
    }

    /**
     * Create a ClickGuard with specific guard period: <code>guardPeriodMillis<code/>.
     *
     * @return The ClickGuard.
     */
    public static ClickGuard newGuard(long guardPeriodMillis) {
        return new LaxGuard(guardPeriodMillis);
    }

    /**
     * Let an OnClickListener to be a GuardedOnClickListener.
     * Use default guard period: {@link #DEFAULT_GUARD_PERIOD_MILLIS}.
     *
     * @param onClickListener The listener to be wrapped.
     * @return a GuardedOnClickListener.
     */
    public static GuardedOnClickListener wrap(OnClickListener onClickListener) {
        return wrap(onClickListener, newGuard());
    }

    /**
     * Let an OnClickListener to be a GuardedOnClickListener.
     * Use specific guard period: <code>guardPeriodMillis<code/>.
     *
     * @param onClickListener   The listener to be wrapped.
     * @param guardPeriodMillis The specific guard period.
     * @return a GuardedOnClickListener.
     */
    public static GuardedOnClickListener wrap(OnClickListener onClickListener, long guardPeriodMillis) {
        return wrap(onClickListener, newGuard(guardPeriodMillis));
    }

    /**
     * Let an OnClickListener to be a GuardedOnClickListener.
     * Use specific ClickGuard: <code>guard<code/>.
     *
     * @param onClickListener The listener to be wrapped.
     * @param guard           The specific ClickGuard.
     * @return a GuardedOnClickListener.
     */
    public static GuardedOnClickListener wrap(OnClickListener onClickListener, ClickGuard guard) {
        return guard.wrapOnClickListener(onClickListener);
    }

    /**
     * Use a new ClickGuard with default guard period {@link #DEFAULT_GUARD_PERIOD_MILLIS} to guard the View.
     *
     * @param view The view to be guarded.
     * @return The created ClickedGuard.
     */
    public static ClickGuard guard(View view) {
        return guard(view, DEFAULT_GUARD_PERIOD_MILLIS);
    }

    /**
     * Use a new ClickGuard with specific guard period <code>guardPeriodMillis</code> to guard the View.
     *
     * @param view              The view to be guarded.
     * @param guardPeriodMillis The specific guard period.
     * @return The ClickedGuard used to guard this View.
     */
    public static ClickGuard guard(View view, long guardPeriodMillis) {
        return guard(view, newGuard(guardPeriodMillis));
    }

    /**
     * Use a specific ClickGuard <code>guard</code> to guard a View.
     *
     * @param view  The view to be guarded.
     * @param guard The ClickGuard used to guard.
     * @return The ClickedGuard used to guard this View.
     */
    public static ClickGuard guard(View view, ClickGuard guard) {
        return guard.add(view);
    }

    /**
     * Let a view to be guarded by this ClickGuard.
     * In a guard period, only the first clicked view can be notified.
     *
     * @param view The view to be guarded.
     * @return This ClickGuard instance.
     * @see #addAll(android.view.View...)
     */
    public ClickGuard add(View view) {
        if (view == null) {
            throw new IllegalArgumentException("View shouldn't be null!");
        }
        OnClickListener listener = ListenerGetter.get(view);
        if (listener == null) {
            throw new IllegalStateException("Haven't set an OnClickListener to View (id: 0x"
                    + Integer.toHexString(view.getId()) + ")!");
        }
        view.setOnClickListener(wrapOnClickListener(listener));
        return this;
    }

    /**
     * Let a series of views to be guarded by this ClickGuard.
     * In a guard period, only the first clicked view can be notified.
     *
     * @param views The views to be guarded.
     * @return This ClickGuard instance.
     * @see #add(android.view.View)
     */
    public ClickGuard addAll(View... views) {
        for (View view : views) {
            add(view);
        }
        return this;
    }

    public GuardedOnClickListener wrapOnClickListener(OnClickListener onClickListener) {
        return GuardedOnClickListener.wrap(onClickListener, this);
    }

    /**
     * Let the Guard start to guard.
     */
    public abstract void guard();

    /**
     * Let the Guard to have a rest.
     */
    public abstract void relax();

    /**
     * Determine whether the Guard is on duty.
     *
     * @return Whether the Guard is on duty.
     */
    public abstract boolean isGuarding();

    private static class LaxGuard extends ClickGuard {
        private static final int GUARDING = 0;
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final long mGuardPeriodMillis;

        LaxGuard(long guardPeriodMillis) {
            mGuardPeriodMillis = guardPeriodMillis;
        }

        @Override
        public void guard() {
            mHandler.sendEmptyMessageDelayed(GUARDING, mGuardPeriodMillis);
        }

        @Override
        public void relax() {
            mHandler.removeMessages(GUARDING);
        }

        @Override
        public boolean isGuarding() {
            return mHandler.hasMessages(GUARDING);
        }
    }

    /**
     * OnClickListener which can avoid multiple rapid clicks.
     */
    public static abstract class GuardedOnClickListener implements OnClickListener {
        ClickGuard mGuard;
        OnClickListener mWrapped;

        public static GuardedOnClickListener wrap(OnClickListener onClickListener, ClickGuard guard) {
            if (onClickListener == null) {
                throw new IllegalArgumentException("onClickListener shouldn't be null!");
            }
            if (onClickListener instanceof GuardedOnClickListener) {
                throw new IllegalArgumentException("Can't wrap GuardedOnClickListener!");
            }
            return new InnerGuardedOnClickListener(internalUnwrap(onClickListener), guard);
        }

        public static OnClickListener unwrap(GuardedOnClickListener guardedOnClickListener) {
            return internalUnwrap(guardedOnClickListener);
        }

        static OnClickListener internalUnwrap(OnClickListener onClickListener) {
            if (!(onClickListener instanceof GuardedOnClickListener)) {
                return onClickListener;
            }
            GuardedOnClickListener guardedOnClickListener = ((GuardedOnClickListener) onClickListener);
            guardedOnClickListener.mGuard.relax();
            if (guardedOnClickListener.mWrapped == null) {
                throw new IllegalStateException("Non wrapped OnClickListener found");
            }
            return internalUnwrap(guardedOnClickListener.mWrapped);
        }

        public GuardedOnClickListener() {
            this(DEFAULT_GUARD_PERIOD_MILLIS);
        }

        public GuardedOnClickListener(long guardPeriodMillis) {
            this(newGuard(guardPeriodMillis));
        }

        public GuardedOnClickListener(ClickGuard guard) {
            this(null, guard);
        }

        GuardedOnClickListener(OnClickListener onClickListener, ClickGuard guard) {
            mGuard = guard;
            mWrapped = onClickListener;
        }

        @Override
        final public void onClick(View v) {
            if (mGuard.isGuarding()) {
                // Guard is guarding, can't do anything.
                onIgnored();
                return;
            }
            // Guard is relaxing. Run!
            if (mWrapped != null) {
                mWrapped.onClick(v);
            }
            if (onClicked()) {
                // Guard becomes vigilant.
                mGuard.guard();
            }
        }

        /**
         * Called when a click to a view is allowed.
         *
         * @return Determine whether this click has been consumed.
         * If <code>true</code> is returned, the host view will be guarded. All clicks in the upcoming guard period will be ignored.
         * Otherwise, the next click will not be ignored.
         */
        public abstract boolean onClicked();

        /**
         * Called when a click to a view is ignored.
         */
        public void onIgnored() {
        }

        public ClickGuard getClickGuard() {
            return mGuard;
        }
    }

    static class InnerGuardedOnClickListener extends GuardedOnClickListener {
        InnerGuardedOnClickListener(OnClickListener onClickListener, ClickGuard guard) {
            super(onClickListener, guard);
        }

        public boolean onClicked() {
            return true;
        }

        public void onIgnored() {
        }
    }

    /**
     * Class used for retrieve OnClickListener from a View.
     */
    static abstract class ListenerGetter {

        private static ListenerGetter IMPL;

        static {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                IMPL = new ListenerGetterIcs();
            } else {
                IMPL = new ListenerGetterBase();
            }
        }

        static OnClickListener get(View view) {
            return IMPL.getOnClickListener(view);
        }

        static Field getField(Class clazz, String fieldName) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                throw new RuntimeException("Can't get " + fieldName + " of " + clazz.getName());
            }
        }

        static Field getField(String className, String fieldName) {
            try {
                return getField(Class.forName(className), fieldName);
            } catch (ClassNotFoundException ignored) {
                throw new RuntimeException("Can't find class: " + className);
            }
        }

        static Object getFieldValue(Field field, Object object) {
            try {
                return field.get(object);
            } catch (IllegalAccessException ignored) {
            }
            return null;
        }

        abstract OnClickListener getOnClickListener(View view);

        private static class ListenerGetterBase extends ListenerGetter {
            private Field mOnClickListenerField;

            ListenerGetterBase() {
                mOnClickListenerField = getField(View.class, "mOnClickListener");
            }

            @Override
            public OnClickListener getOnClickListener(View view) {
                return (OnClickListener) getFieldValue(mOnClickListenerField, view);
            }
        }

        private static class ListenerGetterIcs extends ListenerGetter {
            private Field mListenerInfoField;
            private Field mOnClickListenerField;

            ListenerGetterIcs() {
                mListenerInfoField = getField(View.class, "mListenerInfo");
                mListenerInfoField.setAccessible(true);
                mOnClickListenerField = getField("android.view.View$ListenerInfo", "mOnClickListener");
            }

            @Override
            public OnClickListener getOnClickListener(View view) {
                Object listenerInfo = getFieldValue(mListenerInfoField, view);
                return listenerInfo != null ? (OnClickListener) getFieldValue(mOnClickListenerField, listenerInfo) : null;
            }
        }
    }
}
