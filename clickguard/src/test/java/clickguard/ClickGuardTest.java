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

    @Test
    public void guardActsInTheRightWay() {
        ClickGuard guard = ClickGuard.newGuard(10000);
        guard.guard();
        assertTrue(guard.isGuarding());
        guard.relax();
        assertFalse(guard.isGuarding());
    }

    @Test
    public void guardShouldBeLaxAfterSpecifiedTime() {
        ClickGuard guard = ClickGuard.newGuard(1000);
        long start = SystemClock.elapsedRealtime();
        guard.guard();
        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        assertFalse(guard.isGuarding());
        assertTrue((SystemClock.elapsedRealtime() - start) == 1000);
    }

    @Test
    public void baseGuardedOnClickListenerTest() {
        CountClickListener listener = new CountClickListener();
        ClickGuard.GuardedOnClickListener guardedListener
                = new ClickGuard.InnerGuardedOnClickListener(listener, ClickGuard.newGuard(1000));

        ClickGuard guard = guardedListener.getClickGuard();
        assertFalse(guard.isGuarding());

        guardedListener.onClick(null);
        assertEquals(1, listener.getClickedCount());
        assertTrue(guard.isGuarding());

        guardedListener.onClick(null);
        guardedListener.onClick(null);
        assertEquals(1, listener.getClickedCount());

        guard.relax();
        assertFalse(guard.isGuarding());

        guardedListener.onClick(null);
        assertEquals(2, listener.getClickedCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenGuardNullView() {
        ClickGuard.guard(null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowWhenGuardNonListenerView() {
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
    public void retrieveTheRightListener() {
        View view = new View(Robolectric.application);
        View.OnClickListener listener = new CountClickListener();
        view.setOnClickListener(listener);
        assertEquals(listener, ClickGuard.ListenerGetter.get(view));
    }

    @Test
    public void guardedViewPreventsMultipleClicks() {
        View view = new View(Robolectric.application);
        CountClickListener listener = new CountClickListener();
        view.setOnClickListener(listener);
        ClickGuard guard = ClickGuard.newGuard(10000).add(view);
        assertFalse(guard.isGuarding());

        clickView(view, 1);
        assertEquals(1, listener.getClickedCount());
        assertTrue(guard.isGuarding());

        clickView(view, 5);
        assertTrue(guard.isGuarding());
        assertEquals(1, listener.getClickedCount());

        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        assertFalse(guard.isGuarding());

        clickView(view, 1);
        assertTrue(guard.isGuarding());
        assertEquals(2, listener.getClickedCount());

        guard.relax();
        assertFalse(guard.isGuarding());

        clickView(view, 1);
        assertEquals(3, listener.getClickedCount());
        clickView(view, 5);
        assertEquals(3, listener.getClickedCount());
    }

    private static void clickView(View view, int count) {
        for (int i = 0; i < count; i++) {
            view.performClick();
        }
    }
}
