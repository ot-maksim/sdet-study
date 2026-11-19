package com.study.sut.web;

import com.study.sut.users.NewUser;
import com.study.sut.users.User;
import com.study.sut.users.UserRejectedException;
import com.study.sut.users.UserService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.List;

/**
 * Server-rendered HTML page at {@code /users}: a list of users, a create form and a delete button per row.
 * Post/Redirect/Get: a successful submit answers 303 to {@code /users?created={id}}, a rejected one
 * re-renders the form with status 400, the error and the entered values.
 *
 * <p>Stable hooks for UI tests are {@code data-testid} attributes; they are the page's public interface,
 * like paths of the REST API. The layout is responsive: below 600px the table turns into cards and the
 * form is collapsed behind a "New user" toggle — the same test ids, a different interaction.
 */
public final class UsersPage {

    private final UserService users;

    public UsersPage(UserService users) {
        this.users = users;
    }

    public void register(Javalin app) {
        app.get("/", ctx -> ctx.redirect("/users", HttpStatus.FOUND));
        app.get("/users", this::show);
        app.post("/users", this::submit);
        app.post("/users/{id}/delete", ctx -> {
            long id = UsersApi.id(ctx);
            users.delete(id);
            ctx.redirect("/users?deleted=" + id, HttpStatus.SEE_OTHER);
        });
    }

    private void show(Context ctx) {
        String notice = null;
        if (ctx.queryParam("created") != null) {
            notice = "User created";
        } else if (ctx.queryParam("deleted") != null) {
            notice = "User deleted";
        }
        ctx.html(render(users.all(), Form.empty(), notice, null));
    }

    private void submit(Context ctx) {
        Form form = new Form(ctx.formParam("name"), ctx.formParam("email"), ctx.formParam("age"), ctx.formParam("country"));
        try {
            long id = users.create(form.toRequest()).id();
            ctx.redirect("/users?created=" + id, HttpStatus.SEE_OTHER);
        } catch (UserRejectedException e) {
            ctx.status(e.reason() == UserRejectedException.Reason.CONFLICT ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST)
                    .html(render(users.all(), form, null, e));
        }
    }

    /** What the user typed, kept verbatim to re-render after a rejection. */
    private record Form(String name, String email, String age, String country) {

        static Form empty() {
            return new Form("", "", "", "");
        }

        NewUser toRequest() {
            Integer parsedAge = null;
            if (age != null && !age.isBlank()) {
                try {
                    parsedAge = Integer.valueOf(age.strip());
                } catch (NumberFormatException e) {
                    throw UserRejectedException.invalid("age_invalid", "Age must be a number: " + age);
                }
            }
            return new NewUser(name, email, parsedAge, country);
        }
    }

    private static String render(List<User> all, Form form, String notice, UserRejectedException error) {
        StringBuilder rows = new StringBuilder();
        for (User u : all) {
            rows.append("""
                    <tr data-testid="user-row" data-user-id="%d">
                      <td data-testid="user-name" data-label="Name">%s</td>
                      <td data-testid="user-email" data-label="Email">%s</td>
                      <td data-testid="user-age" data-label="Age">%d</td>
                      <td data-testid="user-country" data-label="Country">%s</td>
                      <td><form method="post" action="/users/%d/delete">
                        <button type="submit" data-testid="delete-user" aria-label="Delete %s">Delete</button>
                      </form></td>
                    </tr>
                    """.formatted(u.id(), esc(u.name()), esc(u.email()), u.age(), esc(u.country()), u.id(), esc(u.name())));
        }
        String noticeHtml = notice == null ? "" : "<p role=\"status\" data-testid=\"notice\">" + esc(notice) + "</p>";
        String errorHtml = error == null ? ""
                : "<p role=\"alert\" data-testid=\"form-error\" data-error-code=\"%s\">%s</p>".formatted(esc(error.code()), esc(error.getMessage()));
        String empty = all.isEmpty() ? "<p data-testid=\"no-users\">No users yet</p>" : "";

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Users</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 0 auto; max-width: 960px; padding: 16px; }
                    table { border-collapse: collapse; width: 100%%; }
                    th, td { border-bottom: 1px solid #ddd; padding: 6px 8px; text-align: left; }
                    form.new-user { display: grid; gap: 8px; grid-template-columns: repeat(4, 1fr) auto; margin: 12px 0; }
                    [role=alert] { color: #b00020; }
                    [role=status] { color: #0a6b2d; }
                    @media (max-width: 600px) {
                      form.new-user { grid-template-columns: 1fr; }
                      thead { display: none; }
                      tr, td { display: block; }
                      tr { border: 1px solid #ddd; border-radius: 8px; margin-bottom: 8px; padding: 4px; }
                      td { border: none; }
                      td[data-label]::before { content: attr(data-label) ": "; font-weight: 600; }
                    }
                  </style>
                </head>
                <body>
                  <h1>Users</h1>
                  %s
                  <details data-testid="new-user-section"%s>
                    <summary data-testid="new-user-toggle">New user</summary>
                    <form class="new-user" method="post" action="/users" data-testid="new-user-form">
                      <input name="name" placeholder="Name" aria-label="Name" data-testid="name-input" value="%s">
                      <input name="email" placeholder="Email" aria-label="Email" data-testid="email-input" value="%s">
                      <input name="age" placeholder="Age" aria-label="Age" data-testid="age-input" value="%s">
                      <input name="country" placeholder="Country (DE)" aria-label="Country" data-testid="country-input" value="%s">
                      <button type="submit" data-testid="create-user">Create</button>
                    </form>
                    %s
                  </details>
                  %s
                  <table data-testid="users-table">
                    <thead><tr><th>Name</th><th>Email</th><th>Age</th><th>Country</th><th></th></tr></thead>
                    <tbody>
                %s    </tbody>
                  </table>
                  <script>
                    // Desktop shows the form; a phone gets it collapsed unless there is an error to show
                    const section = document.querySelector('[data-testid=new-user-section]');
                    if (!section.hasAttribute('data-keep-open')) {
                      section.open = !window.matchMedia('(max-width: 600px)').matches;
                    }
                  </script>
                </body>
                </html>
                """.formatted(
                noticeHtml,
                error == null ? "" : " open data-keep-open",
                esc(form.name()), esc(form.email()), esc(form.age()), esc(form.country()),
                errorHtml,
                empty,
                rows);
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
