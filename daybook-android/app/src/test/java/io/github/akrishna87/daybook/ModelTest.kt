package io.github.akrishna87.daybook

import io.github.akrishna87.daybook.model.DEFAULT_NOTEBOOK_ID
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.INBOX_ID
import io.github.akrishna87.daybook.model.Note
import io.github.akrishna87.daybook.model.NoteSort
import io.github.akrishna87.daybook.model.Notebook
import io.github.akrishna87.daybook.model.Project
import io.github.akrishna87.daybook.model.Repeat
import io.github.akrishna87.daybook.model.Settings
import io.github.akrishna87.daybook.model.Task
import io.github.akrishna87.daybook.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ModelTest {
    @Test
    fun everythingSurvivesSavingAndLoading() {
        val work = Project(name = "Work", color = 0xFF299438.toInt())
        val ideas = Notebook(name = "Ideas")
        val parent = Task(title = "Launch", projectId = work.id, priority = 1, due = LocalDate.of(2026, 10, 9), time = LocalTime.of(9, 30), repeat = Repeat.WEEKLY, labels = listOf("deep"))
        val data = Data(
            tasks = listOf(parent, Task(title = "Draft", parentId = parent.id, noteId = "n1", reminder = false, done = true, doneAt = 9)),
            projects = listOf(work),
            notes = listOf(Note(id = "n1", title = "Plan", body = "[ ] a", notebookId = ideas.id, tags = listOf("q4"), pinned = true, trashedAt = 0)),
            notebooks = listOf(Notebook.Default, ideas),
            settings = Settings(theme = ThemeMode.DARK, weekStartsSunday = true, reminders = false, noteSort = NoteSort.TITLE, askedNotifications = true),
        )
        assertEquals(data, Data.fromJson(JSONObject(data.toJson().toString())))
    }

    @Test
    fun daybookOneDataCarriesOver() {
        // The first version had events, simpler tasks and notes, and lock screen settings.
        val v1 = """
            {"events":[{"id":"e1","title":"Team sync","date":"2026-10-03","endDate":"2026-10-03","start":"08:00","end":"09:00","color":1}],
             "tasks":[{"id":"t1","title":"Buy milk","done":false,"due":"2026-10-04","color":5}],
             "notes":[{"id":"n1","title":"Old","body":"text","pinned":true,"color":0,"updatedAt":7}],
             "settings":{"background":1,"weekStartsSunday":true}}
        """.trimIndent()
        val d = Data.fromJson(JSONObject(v1))
        val event = d.tasks.single { it.id == "e1" }
        assertEquals("Team sync", event.title)
        assertEquals(LocalDate.of(2026, 10, 3), event.due)
        assertEquals(LocalTime.of(8, 0), event.time)
        assertEquals(INBOX_ID, event.projectId)
        assertEquals(LocalDate.of(2026, 10, 4), d.tasks.single { it.id == "t1" }.due)
        val note = d.notes.single()
        assertEquals(DEFAULT_NOTEBOOK_ID, note.notebookId)
        assertEquals(7L, note.updatedAt)
        assertEquals(7L, note.createdAt)
        assertTrue(d.settings.weekStartsSunday)
        assertEquals(listOf(Notebook.Default), d.notebooks)
    }

    @Test
    fun orphansFindAHome() {
        val json = """{"tasks":[{"id":"t","title":"x","projectId":"gone"}],"notes":[{"id":"n","notebookId":"gone","body":"b"}],"notebooks":[{"id":"nb","name":"Only"}]}"""
        val d = Data.fromJson(JSONObject(json))
        assertEquals(INBOX_ID, d.tasks.single().projectId)
        assertEquals("nb", d.notes.single().notebookId)
    }

    @Test
    fun labelsAndTagsAreCollected() {
        val d = Data(
            tasks = listOf(Task(title = "a", labels = listOf("Home", "errands")), Task(title = "b", labels = listOf("home"))),
            notes = listOf(Note(tags = listOf("b", "a")), Note(tags = listOf("hidden"), trashedAt = 5)),
        )
        assertEquals(listOf("errands", "Home"), d.labels)
        assertEquals(listOf("a", "b"), d.tags)
    }

    @Test
    fun firstRunTeachesTheBasics() {
        val d = Data.firstRun()
        assertEquals("Welcome to Daybook", d.notes.single().title)
        assertEquals(3, d.tasks.size)
        assertTrue(d.tasks.all { it.projectId == INBOX_ID && it.due == null })
    }
}
