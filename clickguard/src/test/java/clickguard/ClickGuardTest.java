package clickguard;

import android.os.SystemClock;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ClickGuardTest {

    private static class CountClickListener implements View.OnClickListener {
        int clickedCount = 0;

        @Override
        public void onClick(View v) {
            clickedCount++;
        }

        public int getClickedCount() {
            return clickedCount;
        }
    }

    private static class CountClickGuardedOnClickListener extends ClickGuard.GuardedOnClickListener {
        int clickedCount = 0;

        @Override
        public boolean onClicked() {
            clickedCount++;
            return true;
        }

        @Override
        public void onIgnored() {
            assertTrue(getClickGuard().isWatching());
        }
    }

    @Test
    public void guardActsInTheRightWay() {
        ClickGuard guard = ClickGuard.newGuard(10000);
        guard.watch();
        assertTrue(guard.isWatching());
        guard.rest();
        assertFalse(guard.isWatching());
    }

    @Test
    public void guardShouldRestWhenWatchPeriodEnds() {
        ClickGuard guard = ClickGuard.newGuard(1000);
        long start = SystemClock.elapsedRealtime();
        guard.watch();
        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        assertFalse(guard.isWatching());
        assertTrue((SystemClock.elapsedRealtime() - start) == 1000);
    }

    @Test
    public void baseGuardedOnClickListenerTest() {
        CountClickListener listener = new CountClickListener();
        ClickGuard.GuardedOnClickListener guardedListener
                = new ClickGuard.InnerGuardedOnClickListener(listener, ClickGuard.newGuard(1000));

        ClickGuard guard = guardedListener.getClickGuard();
        assertFalse(guard.isWatching());

        guardedListener.onClick(null);
        assertEquals(1, listener.getClickedCount());
        assertTrue(guard.isWatching());

        guardedListener.onClick(null);
        guardedListener.onClick(null);
        assertEquals(1, listener.getClickedCount());

        guard.rest();
        assertFalse(guard.isWatching());

        guardedListener.onClick(null);
        assertEquals(2, listener.getClickedCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenGuardNullView() {
        ClickGuard.guard(null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowWhenGuardViewWithoutOnClickListener() {
        ClickGuard.guard(new View(Robolectric.application));
    }

    @Test
    public void wrappedListenerPreventsMultipleClicks() {
        View view = new View(Robolectric.application);
        CountClickListener listener = new CountClickListener();
        view.setOnClickListener(ClickGuard.wrap(listener));

        clickView(view, 5);
        assertEquals(1, listener.getClickedCount());

        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        clickView(view, 5);
        assertEquals(2, listener.getClickedCount());
    }

    @Test
    public void guardedListenerPreventsMultipleClicks() {
        View view = new View(Robolectric.application);
        CountClickGuardedOnClickListener listener = new CountClickGuardedOnClickListener();
        view.setOnClickListener(listener);

        clickView(view, 5);
        assertEquals(1, listener.clickedCount);

        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        clickView(view, 5);
        assertEquals(2, listener.clickedCount);
    }

    @Test
    public void retrieveTheRightOnClickListener() {
        View view = new View(Robolectric.application);
        View.OnClickListener listener = new CountClickListener();
        view.setOnClickListener(listener);
        assertEquals(listener, ClickGuard.retrieveOnClickListener(view));
    }

    @Test
    public void guardedViewPreventsMultipleClicks() {
        View view = new View(Robolectric.application);
        CountClickListener listener = new CountClickListener();
        view.setOnClickListener(listener);
        ClickGuard guard = ClickGuard.newGuard(10000).add(view);
        assertFalse(guard.isWatching());

        clickView(view, 1);
        assertEquals(1, listener.getClickedCount());
        assertTrue(guard.isWatching());

        clickView(view, 5);
        assertTrue(guard.isWatching());
        assertEquals(1, listener.getClickedCount());

        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        assertFalse(guard.isWatching());

        clickView(view, 1);
        assertTrue(guard.isWatching());
        assertEquals(2, listener.getClickedCount());

        guard.rest();
        assertFalse(guard.isWatching());

        clickView(view, 1);
        assertEquals(3, listener.getClickedCount());
        clickView(view, 5);
        assertEquals(3, listener.getClickedCount());
    }

    @Test
    public void guardMultipleViews() {
        CountClickListener listener = new CountClickListener() {
            @Override
            public void onClick(View v) {
                super.onClick(v);
                assertEquals(1, getClickedCount());
            }
        };
        View view1 = new View(Robolectric.application);
        view1.setOnClickListener(listener);
        View view2 = new View(Robolectric.application);
        view2.setOnClickListener(listener);
        View view3 = new View(Robolectric.application);
        view3.setOnClickListener(listener);

        ClickGuard.guard(view1, view2, view3);

        clickViews(view1, view2, view3);
    }

    private static void clickView(View view, int count) {
        for (int i = 0; i < count; i++) {
            view.performClick();
        }
    }

    private void clickViews(View... views) {
        for (View view : views) {
            view.performClick();
            view.setOnClickListener(new ClickGuard.GuardedOnClickListener() {
                @Override
                public boolean onClicked() {
                    return true;
                }
            });
        }
    }
}
