Our task is to modify how Failter interfaces with its users, and how it tracks LLM usage.
 * We are shifting away from reporting monetary costs, as that was not working.
   Instead, we'll report token counts in and out, where we were reporting cost.
 * We will accept a config file that defines the experiment parts, rather than a fixed directory structure.
 * We will report results in a hierachical JSON structure, rather than trying to
   fit everything into a tabular format. The reporting utility may still produce
   the human-readable table, but programs are better served by the JSON.

We must be very careful not change things that aren't directly related to these concerns,
to make it easy to see what has changed.

The improvement proposal gives details. We seek to implement all of those items.


----

Considerations to keep in mind:

We still want to be able to produce a file of the results, as well as write it to stdout.

We want a run to be idempotent.  If a particular model call fails, it should go on with the others.
If I start the run again, with the same parameters, it should not re-do the ones that succeeded,
only the ones that failed.  Keeping the state on disk makes this easier.
