import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.util.Scanner;
class AppLogger {
    private static final Logger logger = Logger.getLogger(AppLogger.class.getName());
    static {
        logger.setLevel(Level.INFO); 
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        logger.addHandler(consoleHandler);
        try {
            FileHandler fileHandler = new FileHandler("astronaut_schedule.log", true); 
            fileHandler.setLevel(Level.ALL); 
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to set up file logger.", e);
        }
    }

    public static Logger getLogger() {
        return logger;
    }
}

// --- 2. Task Class ---
class Task {
    private String description;
    private LocalTime startTime;
    private LocalTime endTime;
    private Priority priority;
    private boolean completed;
    public Task(String description, LocalTime startTime, LocalTime endTime, Priority priority) {
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time cannot be after end time.");
        }
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.priority = priority;
        this.completed = false;
    }

    public String getDescription() {
        return description;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Priority getPriority() {
        return priority;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markAsCompleted() {
        this.completed = true;
    }

    public void updateTask(String description, LocalTime startTime, LocalTime endTime, Priority priority) {
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time cannot be after end time.");
        }
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.priority = priority;
    }
    public boolean overlapsWith(Task other) {
        return this.startTime.isBefore(other.endTime) && this.endTime.isAfter(other.startTime);
    }

    @Override
    public String toString() {
        String status = completed ? " [COMPLETED]" : "";
        return String.format("%s - %s: %s [%s]%s",
                startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                endTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                description, priority, status);
    }
}

enum Priority {
    LOW, MEDIUM, HIGH;

    public static Priority fromString(String text) {
        for (Priority p : Priority.values()) {
            if (p.name().equalsIgnoreCase(text)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Invalid priority level: " + text);
    }
}

// --- 3. Task Factory Pattern ---
class TaskFactory {
    public static Task createTask(String description, String startTimeStr, String endTimeStr, String priorityStr)
            throws DateTimeParseException, IllegalArgumentException {
        LocalTime startTime = LocalTime.parse(startTimeStr, java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(endTimeStr, java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Priority priority = Priority.fromString(priorityStr);
        return new Task(description, startTime, endTime, priority);
    }
}

// --- 4. Observer Pattern Interfaces ---
interface TaskConflictObserver {
    void onTaskConflict(Task newTask, Task conflictingTask);
}

interface TaskUpdateObserver {
    void onTaskUpdate(Task updatedTask);
}

// --- 5. Schedule Manager (Singleton Pattern) ---
class ScheduleManager {
    private static ScheduleManager instance;
    private final List<Task> tasks;
    private final List<TaskConflictObserver> conflictObservers;
    private final List<TaskUpdateObserver> updateObservers;
    private static final Logger logger = AppLogger.getLogger();

    private ScheduleManager() {
        tasks = new ArrayList<>();
        conflictObservers = new ArrayList<>();
        updateObservers = new ArrayList<>();
    }

    public static synchronized ScheduleManager getInstance() {
        if (instance == null) {
            instance = new ScheduleManager();
        }
        return instance;
    }

    public void addConflictObserver(TaskConflictObserver observer) {
        conflictObservers.add(observer);
    }

    public void addUpdateObserver(TaskUpdateObserver observer) {
        updateObservers.add(observer);
    }

    private void notifyConflictObservers(Task newTask, Task conflictingTask) {
        for (TaskConflictObserver observer : conflictObservers) {
            observer.onTaskConflict(newTask, conflictingTask);
        }
    }

    private void notifyUpdateObservers(Task updatedTask) {
        for (TaskUpdateObserver observer : updateObservers) {
            observer.onTaskUpdate(updatedTask);
        }
    }

    public void addTask(Task newTask) throws TaskConflictException {
        for (Task existingTask : tasks) {
            if (newTask.overlapsWith(existingTask)) {
                notifyConflictObservers(newTask, existingTask);
                logger.warning(String.format("Task conflict detected: New task '%s' conflicts with existing task '%s'",
                        newTask.getDescription(), existingTask.getDescription()));
                throw new TaskConflictException("Task conflicts with existing task \"" + existingTask.getDescription() + "\".");
            }
        }

        tasks.add(newTask);
        Collections.sort(tasks, Comparator.comparing(Task::getStartTime)); 
        logger.info(String.format("Task added: %s", newTask.getDescription()));
    }

    public void removeTask(String description) throws TaskNotFoundException {
        boolean removed = tasks.removeIf(task -> task.getDescription().equalsIgnoreCase(description));
        if (!removed) {
            logger.warning(String.format("Attempted to remove non-existent task: %s", description));
            throw new TaskNotFoundException("Task not found.");
        }
        logger.info(String.format("Task removed: %s", description));
    }

    public List<Task> viewAllTasks() {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tasks);
    }

    public List<Task> viewTasksByPriority(Priority priority) {
        List<Task> filteredTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getPriority().equals(priority)) {
                filteredTasks.add(task);
            }
        }
        Collections.sort(filteredTasks, Comparator.comparing(Task::getStartTime));
        return filteredTasks;
    }
    public void editTask(String oldDescription, String newDescription, String startTimeStr, String endTimeStr, String priorityStr)
            throws TaskNotFoundException, DateTimeParseException, IllegalArgumentException, TaskConflictException {
        Task taskToEdit = null;
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getDescription().equalsIgnoreCase(oldDescription)) {
                taskToEdit = tasks.get(i);
                index = i;
                break;
            }
        }

        if (taskToEdit == null) {
            logger.warning(String.format("Attempted to edit non-existent task: %s", oldDescription));
            throw new TaskNotFoundException("Task to edit not found.");
        }
        LocalTime newStartTime = LocalTime.parse(startTimeStr, java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime newEndTime = LocalTime.parse(endTimeStr, java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Priority newPriority = Priority.fromString(priorityStr);
        Task tempTask = new Task(newDescription, newStartTime, newEndTime, newPriority);
        for (int i = 0; i < tasks.size(); i++) {
            if (i != index && tempTask.overlapsWith(tasks.get(i))) {
                notifyConflictObservers(tempTask, tasks.get(i));
                logger.warning(String.format("Edit conflict detected: Updated task '%s' conflicts with existing task '%s'",
                        newDescription, tasks.get(i).getDescription()));
                throw new TaskConflictException("Edited task conflicts with existing task \"" + tasks.get(i).getDescription() + "\".");
            }
        }

        taskToEdit.updateTask(newDescription, newStartTime, newEndTime, newPriority);
        Collections.sort(tasks, Comparator.comparing(Task::getStartTime)); 
        notifyUpdateObservers(taskToEdit);
        logger.info(String.format("Task edited: %s -> %s", oldDescription, newDescription));
    }
    public void markTaskAsCompleted(String description) throws TaskNotFoundException {
        Task taskToComplete = null;
        for (Task task : tasks) {
            if (task.getDescription().equalsIgnoreCase(description)) {
                taskToComplete = task;
                break;
            }
        }

        if (taskToComplete == null) {
            logger.warning(String.format("Attempted to mark non-existent task as completed: %s", description));
            throw new TaskNotFoundException("Task not found.");
        }
        taskToComplete.markAsCompleted();
        notifyUpdateObservers(taskToComplete);
        logger.info(String.format("Task marked as completed: %s", description));
    }
}
class TaskConflictException extends Exception {
    public TaskConflictException(String message) {
        super(message);
    }
}

class TaskNotFoundException extends Exception {
    public TaskNotFoundException(String message) {
        super(message);
    }
}
class ConsoleNotifier implements TaskConflictObserver, TaskUpdateObserver {
    @Override
    public void onTaskConflict(Task newTask, Task conflictingTask) {
        System.err.println("\nALERT: New task '" + newTask.getDescription() + "' conflicts with existing task '" + conflictingTask.getDescription() + "'!");
    }

    @Override
    public void onTaskUpdate(Task updatedTask) {
        System.out.println("\nINFO: Task '" + updatedTask.getDescription() + "' has been updated/completed.");
    }
}


public class AstronautScheduleApp {
    private static final Logger logger = AppLogger.getLogger();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ScheduleManager scheduleManager = ScheduleManager.getInstance();
        ConsoleNotifier notifier = new ConsoleNotifier();

        scheduleManager.addConflictObserver(notifier);
        scheduleManager.addUpdateObserver(notifier);

        logger.info("Astronaut Schedule Application Started.");

        while (true) {
            System.out.println("\n--- Astronaut Daily Schedule ---");
            System.out.println("1. Add Task");
            System.out.println("2. Remove Task");
            System.out.println("3. View All Tasks");
            System.out.println("4. View Tasks by Priority");
            System.out.println("5. Edit Task"); 
            System.out.println("6. Mark Task as Completed");
            System.out.println("7. Exit");
            System.out.print("Enter your choice: ");

            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1":
                        addTask(scanner, scheduleManager);
                        break;
                    case "2":
                        removeTask(scanner, scheduleManager);
                        break;
                    case "3":
                        viewAllTasks(scheduleManager);
                        break;
                    case "4":
                        viewTasksByPriority(scanner, scheduleManager);
                        break;
                    case "5": 
                        editTask(scanner, scheduleManager);
                        break;
                    case "6": 
                        markTaskAsCompleted(scanner, scheduleManager);
                        break;
                    case "7":
                        System.out.println("Exiting application. Goodbye, Astronaut!");
                        logger.info("Astronaut Schedule Application Exited.");
                        return;
                    default:
                        System.err.println("Invalid choice. Please try again.");
                        logger.warning("Invalid menu choice entered: " + choice);
                }
            } catch (DateTimeParseException e) {
                System.err.println("Error: Invalid time format. Please use HH:mm (e.g., 09:00).");
                logger.log(Level.WARNING, "Invalid time format input", e);
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                logger.log(Level.WARNING, "Invalid argument input", e);
            } catch (TaskConflictException | TaskNotFoundException e) {
                System.err.println("Error: " + e.getMessage());
                logger.log(Level.INFO, "Application specific error", e); 
            } catch (Exception e) { 
                System.err.println("An unexpected error occurred: " + e.getMessage());
                logger.log(Level.SEVERE, "An unexpected error occurred", e);
            }
        }
    }

    private static void addTask(Scanner scanner, ScheduleManager scheduleManager)
            throws DateTimeParseException, IllegalArgumentException, TaskConflictException {
        System.out.print("Enter task description: ");
        String description = scanner.nextLine();
        System.out.print("Enter start time (HH:mm): ");
        String startTimeStr = scanner.nextLine();
        System.out.print("Enter end time (HH:mm): ");
        String endTimeStr = scanner.nextLine();
        System.out.print("Enter priority (LOW, MEDIUM, HIGH): ");
        String priorityStr = scanner.nextLine();

        Task newTask = TaskFactory.createTask(description, startTimeStr, endTimeStr, priorityStr);
        scheduleManager.addTask(newTask);
        System.out.println("Task added successfully. No conflicts.");
    }

    private static void removeTask(Scanner scanner, ScheduleManager scheduleManager)
            throws TaskNotFoundException {
        System.out.print("Enter description of task to remove: ");
        String description = scanner.nextLine();
        scheduleManager.removeTask(description);
        System.out.println("Task removed successfully.");
    }

    private static void viewAllTasks(ScheduleManager scheduleManager) {
        List<Task> tasks = scheduleManager.viewAllTasks();
        if (tasks.isEmpty()) {
            System.out.println("No tasks scheduled for the day.");
        } else {
            System.out.println("\n--- All Scheduled Tasks ---");
            for (int i = 0; i < tasks.size(); i++) {
                System.out.println((i + 1) + ". " + tasks.get(i));
            }
        }
    }

    private static void viewTasksByPriority(Scanner scanner, ScheduleManager scheduleManager) {
        System.out.print("Enter priority level to view (LOW, MEDIUM, HIGH): ");
        String priorityStr = scanner.nextLine();
        try {
            Priority priority = Priority.fromString(priorityStr);
            List<Task> tasks = scheduleManager.viewTasksByPriority(priority);
            if (tasks.isEmpty()) {
                System.out.println("No tasks found for priority: " + priority.name());
            } else {
                System.out.println("\n--- Tasks with Priority " + priority.name() + " ---");
                for (int i = 0; i < tasks.size(); i++) {
                    System.out.println((i + 1) + ". " + tasks.get(i));
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            logger.log(Level.WARNING, "Invalid priority level input", e);
        }
    }

    private static void editTask(Scanner scanner, ScheduleManager scheduleManager)
            throws TaskNotFoundException, DateTimeParseException, IllegalArgumentException, TaskConflictException {
        System.out.print("Enter description of task to edit: ");
        String oldDescription = scanner.nextLine();
        System.out.print("Enter NEW description for the task: ");
        String newDescription = scanner.nextLine();
        System.out.print("Enter NEW start time (HH:mm): ");
        String startTimeStr = scanner.nextLine();
        System.out.print("Enter NEW end time (HH:mm): ");
        String endTimeStr = scanner.nextLine();
        System.out.print("Enter NEW priority (LOW, MEDIUM, HIGH): ");
        String priorityStr = scanner.nextLine();

        scheduleManager.editTask(oldDescription, newDescription, startTimeStr, endTimeStr, priorityStr);
        System.out.println("Task '" + oldDescription + "' successfully updated.");
    }

    private static void markTaskAsCompleted(Scanner scanner, ScheduleManager scheduleManager)
            throws TaskNotFoundException {
        System.out.print("Enter description of task to mark as completed: ");
        String description = scanner.nextLine();
        scheduleManager.markTaskAsCompleted(description);
        System.out.println("Task '" + description + "' marked as completed.");
    }

}
