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

/**
 * Class used to guard a view to avoid multiple rapid clicks.
 * <p/>
 * Guarding a view is as easy as:
 * <pre><code>
 * ClickGuard.guard(view);
 * </code></pre>
 * <p/>
 * Or:
 * <pre><code>
 * ClickGuard.newGuard().add(view);
 * </code></pre>
 * <p/>
 * When a guarded view is clicked, the view will be watched for a period of time from that moment.
 * All the upcoming click events will be ignored until the watch period ends.
 * <p/>
 * By default, watch period is 1000 milliseconds. You can create a ClickGuard using specify watch
 * period like this:
 * <pre><code>
 * ClickGuard.newGuard(600); // Create a ClickGuard with 600ms watch period.
 * </code></pre>
 * <p/>
 * Multiple views can be guarded by a ClickGuard simultaneously:
 * <pre><code>
 * ClickGuard.guard(view1, view2, view3);
 * </code></pre>
 * <p/>
 * When multiple views are guarded by one ClickGuard, the first click on a view will trigger this
 * ClickGuard to watch. And all upcoming clicks on any of the guarded views will be ignored until
 * the watch period ends.
 * <p/>
 * Another way to guard a view is using a {@linkplain GuardedOnClickListener GuardedOnClickListener}
 * instead of {@linkplain android.view.View.OnClickListener OnClickListener}:
 * <pre><code>
 * button.setOnClickListener(new GuardedOnClickListener() {
 *     {@literal @Override}
 *     public boolean onClicked() {
 *         // React to button click.
 *         return true;
 *     }
 * });
 * </code></pre>
 * <p/>
 * Using static {@linkplain #wrap(android.view.View.OnClickListener) wrap} method can simply make an
 * exist {@linkplain android.view.View.OnClickListener OnClickListener} to be a {@linkplain
 * GuardedOnClickListener GuardedOnClickListener}:
 * <pre><code>
 * button.setOnClickListener(ClickGuard.wrap(onClickListener));
 * </code></pre>
 */
public abstract class ClickGuard {

    /**
     * Default watch period in millis.
     */
    public static final long DEFAULT_WATCH_PERIOD_MILLIS = 1000L;

    private ClickGuard() {
        // private
    }

    // ---------------------------------------------------------------------------------------------
    //                                  Utility methods start
    // ---------------------------------------------------------------------------------------------

    /**
     * Create a ClickGuard with default watch period: {@link #DEFAULT_WATCH_PERIOD_MILLIS}.
     *
     * @return The created ClickGuard instance.
     */
    public static ClickGuard newGuard() {
        return newGuard(DEFAULT_WATCH_PERIOD_MILLIS);
    }

    /**
     * Create a ClickGuard with specific watch period: {@code watchPeriodMillis}.
     *
     * @return The created ClickGuard instance.
     */
    public static ClickGuard newGuard(long watchPeriodMillis) {
        return new ClickGuardImpl(watchPeriodMillis);
    }

    /**
     * Let the provided {@linkplain android.view.View.OnClickListener OnClickListener} to be a
     * {@linkplain GuardedOnClickListener GuardedOnClickListener}. Use a new guard with default
     * watch period: {@link #DEFAULT_WATCH_PERIOD_MILLIS}.
     *
     * @param onClickListener The listener to be wrapped.
     * @return A GuardedOnClickListener instance.
     */
    public static GuardedOnClickListener wrap(OnClickListener onClickListener) {
        return wrap(newGuard(), onClickListener);
    }

    /**
     * Let the provided {@linkplain android.view.View.OnClickListener OnClickListener} to be a
     * {@linkplain GuardedOnClickListener GuardedOnClickListener}. Use a new guard with specific
     * watch period: {@code watchPeriodMillis}.
     *
     * @param watchPeriodMillis The specific watch period.
     * @param onClickListener   The listener to be wrapped.
     * @return A GuardedOnClickListener instance.
     */
    public static GuardedOnClickListener wrap(long watchPeriodMillis, OnClickListener onClickListener) {
        return newGuard(watchPeriodMillis).wrapOnClickListener(onClickListener);
    }

    /**
     * Let the provided {@linkplain android.view.View.OnClickListener OnClickListener} to be a
     * {@linkplain GuardedOnClickListener GuardedOnClickListener}. Use specific ClickGuard: {@code
     * guard}.
     *
     * @param guard           The specific ClickGuard.
     * @param onClickListener The listener to be wrapped.
     * @return A GuardedOnClickListener instance.
     */
    public static GuardedOnClickListener wrap(ClickGuard guard, OnClickListener onClickListener) {
        return guard.wrapOnClickListener(onClickListener);
    }

    /**
     * Use a new ClickGuard with default watch period {@link #DEFAULT_WATCH_PERIOD_MILLIS} to guard
     * View(s).
     *
     * @param view   The View to be guarded.
     * @param others More views to be guarded.
     * @return The created ClickedGuard.
     */
    public static ClickGuard guard(View view, View... others) {
        return guard(DEFAULT_WATCH_PERIOD_MILLIS, view, others);
    }

    /**
     * Use a new ClickGuard with specific guard period {@code watchPeriodMillis} to guard View(s).
     *
     * @param watchPeriodMillis The specific watch period.
     * @param view              The View to be guarded.
     * @param others            More Views to be guarded.
     * @return The created ClickedGuard.
     */
    public static ClickGuard guard(long watchPeriodMillis, View view, View... others) {
        return guard(newGuard(watchPeriodMillis), view, others);
    }

    /**
     * Use a specific ClickGuard {@code guard} to guard View(s).
     *
     * @param guard  The ClickGuard used to guard.
     * @param view   The View to be guarded.
     * @param others More Views to be guarded.
     * @return The given ClickedGuard itself.
     */
    public static ClickGuard guard(ClickGuard guard, View view, View... others) {
        return guard.addAll(view, others);
    }

    /**
     * Get the ClickGuard from a guarded View.
     *
     * @param view A View guarded by ClickGuard.
     * @return The ClickGuard which guards this View.
     */
    public static ClickGuard get(View view) {
        OnClickListener listener = retrieveOnClickListener(view);
        if (listener instanceof GuardedOnClickListener) {
            return ((GuardedOnClickListener) listener).getClickGuard();
        }
        throw new IllegalStateException("The view (id: 0x" + view.getId() + ") isn't guarded by ClickGuard!");
    }

    /**
     * Retrieve {@linkplain android.view.View.OnClickListener OnClickListener} from a View.
     *
     * @param view The View used to retrieve.
     * @return The retrieved {@linkplain android.view.View.OnClickListener OnClickListener}.
     */
    public static OnClickListener retrieveOnClickListener(View view) {
        if (view == null) {
            throw new NullPointerException("Given view is null!");
        }
        return ListenerGetter.get(view);
    }

    // ---------------------------------------------------------------------------------------------
    //                                  Utility methods end
    // ---------------------------------------------------------------------------------------------

    /**
     * Let a view to be guarded by this ClickGuard.
     *
     * @param view The view to be guarded.
     * @return This ClickGuard instance.
     * @see #addAll(android.view.View, android.view.View...)
     */
    public ClickGuard add(View view) {
        if (view == null) {
            throw new IllegalArgumentException("View shouldn't be null!");
        }
        OnClickListener listener = retrieveOnClickListener(view);
        if (listener == null) {
            throw new IllegalStateException("Haven't set an OnClickListener to View (id: 0x"
                    + Integer.toHexString(view.getId()) + ")!");
        }
        view.setOnClickListener(wrapOnClickListener(listener));
        return this;
    }

    /**
     * Like {@link #add(android.view.View)}. Let a series of views to be guarded by this ClickGuard.
     *
     * @param view   The view to be guarded.
     * @param others More views to be guarded.
     * @return This ClickGuard instance.
     * @see #add(android.view.View)
     */
    public ClickGuard addAll(View view, View... others) {
        add(view);
        for (View v : others) {
            add(v);
        }
        return this;
    }

    /**
     * Let the provided {@link android.view.View.OnClickListener} to be a {@link GuardedOnClickListener}
     * which will be guarded by this ClickGuard.
     *
     * @param onClickListener onClickListener
     * @return A GuardedOnClickListener instance.
     */
    public GuardedOnClickListener wrapOnClickListener(OnClickListener onClickListener) {
        if (onClickListener == null) {
            throw new IllegalArgumentException("onClickListener shouldn't be null!");
        }
        if (onClickListener instanceof GuardedOnClickListener) {
            throw new IllegalArgumentException("Can't wrap GuardedOnClickListener!");
        }
        return new InnerGuardedOnClickListener(onClickListener, this);
    }

    /**
     * Let the Guard to start watching.
     */
    public abstract void watch();

    /**
     * Let the Guard to have a rest.
     */
    public abstract void rest();

    /**
     * Determine whether the Guard is on duty.
     *
     * @return Whether the Guard is watching.
     */
    public abstract boolean isWatching();

    private static class ClickGuardImpl extends ClickGuard {
        private static final int WATCHING = 0;
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final long mWatchPeriodMillis;

        ClickGuardImpl(long watchPeriodMillis) {
            mWatchPeriodMillis = watchPeriodMillis;
        }

        @Override
        public void watch() {
            mHandler.sendEmptyMessageDelayed(WATCHING, mWatchPeriodMillis);
        }

        @Override
        public void rest() {
            mHandler.removeMessages(WATCHING);
        }

        @Override
        public boolean isWatching() {
            return mHandler.hasMessages(WATCHING);
        }
    }

    /**
     * OnClickListener which can avoid multiple rapid clicks.
     */
    public static abstract class GuardedOnClickListener implements OnClickListener {
        private ClickGuard mGuard;
        private OnClickListener mWrapped;

        public GuardedOnClickListener() {
            this(DEFAULT_WATCH_PERIOD_MILLIS);
        }

        public GuardedOnClickListener(long watchPeriodMillis) {
            this(newGuard(watchPeriodMillis));
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
            if (mGuard.isWatching()) {
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
                mGuard.watch();
            }
        }

        /**
         * Called when a click is allowed.
         *
         * @return If {@code true} is returned, the host view will be guarded. All click events in
         * the upcoming watch period will be ignored. Otherwise, the next click will not be ignored.
         */
        public abstract boolean onClicked();

        /**
         * Called when a click is ignored.
         */
        public void onIgnored() {
        }

        public ClickGuard getClickGuard() {
            return mGuard;
        }
    }

    // Inner GuardedOnClickListener implementation.
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
