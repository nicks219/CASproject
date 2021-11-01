package threads;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import threads.Library.Book;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("threads.LibraryTest")
public class LibraryTest {

    int bookCount = 10;
    int bookNumber = bookCount - 1;

    /**
     * Уровень сложности теста
     */
    private static boolean TestHardLevel = true;

    /**
     * Простой однопоточный тест для проверки пограничных условий и счетчиков
     */
    @Test
    public void multithreadLibraryCanGetAndReturnBooksWithCorrectNumbersOnly() throws Library.LibraryExceptions {

        var library = new Library(bookCount);
        Library.Book book = library.tryTakeBook(bookNumber, true);

        Assertions.assertTrue(book != null);
        Assertions.assertNull(library.tryTakeBook(bookNumber, true));
        Assertions.assertTrue(library.tryReturnBook(book));
        Assertions.assertFalse(library.tryReturnBook(book));

        Assertions.assertThrows(Library.LibraryExceptions.class, () -> library.tryTakeBook(-bookCount * 2, true));
        Assertions.assertThrows(Library.LibraryExceptions.class, () -> library.tryTakeBook(bookCount * 2, true));
        Assertions.assertNull(library.tryTakeBook(bookNumber, false));

        book = library.tryTakeBook(bookNumber, true);
        Assertions.assertNotNull(book);
        Assertions.assertEquals(1, library.busyBookCount());
        Assertions.assertEquals(0, library.booksTheftCounterSum());

        library.tryReturnBook(book);
        Assertions.assertEquals(0, library.busyBookCount());
        Assertions.assertEquals(0, library.booksTheftCounterSum());
    }

    /**
     * Тест "под многопоточной нагрузкой" включает в себя три счетчика:
     * 1) один "снаружи" библиотеки на AtomicInteger.
     * 2) два разных "внутри" библиотеки.
     * Тест считается пройденным только, если значения всех трёх совпадут.
     * Также тест содержит счетчик специфических ситуаций errors
     * Тест считается пройденным только, если его значение равно нулю.
     * Слипов внутри потоков нет, они "стучатся" в библиотеку постоянно
     */

    @Test
    public void multithreadLibraryIsThreadSafeConcretely() throws ExecutionException, InterruptedException {

        /* AtomicInteger-счетчик используется как дополнительная проверка */
        AtomicInteger atomicInteger = new AtomicInteger(0);

        Random rnd = new Random();
        int iterations = 1000;
        int task = 100;
        int errors = 0;
        var library = new Library(bookCount);
        CompletableFuture<Book> user1;
        CompletableFuture<Book> user2;
        CompletableFuture<Book> user3;
        CompletableFuture<Boolean> user4;
        CompletableFuture<Boolean> user5;
        CompletableFuture<Boolean> user6;

        while (iterations-- > 0) {

            int number1 = rnd.nextInt(bookCount);
            int number2 = rnd.nextInt(bookCount);
            int number3 = rnd.nextInt(bookCount);

            user1 = CompletableFuture.supplyAsync(() -> getBookByNumber(library, number1, atomicInteger));
            user2 = CompletableFuture.supplyAsync(() -> getBookByNumber(library, number2, atomicInteger));
            user3 = CompletableFuture.supplyAsync(() -> getBookByNumber(library, number3, atomicInteger));

            Book book1 = user1.get();
            Book book2 = user2.get();
            Book book3 = user3.get();

            user4 = CompletableFuture.supplyAsync(() -> returnBook(library, book1, atomicInteger));
            user5 = CompletableFuture.supplyAsync(() -> returnBook(library, book2, atomicInteger));
            user6 = CompletableFuture.supplyAsync(() -> returnBook(library, book3, atomicInteger));

            Boolean result1 = user4.get();
            Boolean result2 = user5.get();
            Boolean result3 = user6.get();

            /* Сценарий, когда не удалось взять книгу, но удалось её вернуть */
            if (book1 == null && result1 == true) {
                errors++;
            }
            if (book2 == null && result2 == true) {
                errors++;
            }
            if (book3 == null && result3 == true) {
                errors++;
            }

            /* Сценарий, когда удалось взять книгу, но не удалось ее вернуть*/
            if (book1 != null && result1 == false) {
                errors++;
            }
            if (book2 != null && result2 == false) {
                errors++;
            }
            if (book3 != null && result3 == false) {
                errors++;
            }

        }

        System.out.println("\nFirst test passed: ");

        System.out.printf("In the reading room: %d books\n", library.busyBookCount());
        System.out.printf("In the book.counter field: %d \n", library.booksTheftCounterSum());
        System.out.println("Atomic integer: " + atomicInteger);

        Assertions.assertEquals(0, errors);
        Assertions.assertEquals(library.busyBookCount(), atomicInteger.get());
        Assertions.assertEquals(library.booksTheftCounterSum(), atomicInteger.get());
    }

    /**
     * Тест запускает большое количество задач для работы с "библиотекой".
     * Количество книг сокращаем до одной.
     * Оценка производится по равенству трех счетчиков.
     * От "уровня сложности" зависит, будет ли выполняться get() на задачах.
     *
     * @throws ExecutionException
     * @throws InterruptedException
     */

    @Test
    public void testFromHellTest() throws ExecutionException, InterruptedException {

        bookCount = 1;

        /* AtomicInteger-счетчик используется как дополнительная проверка */
        AtomicInteger atomicInteger = new AtomicInteger(0);

        Random rnd = new Random();
        int iterations = 1000;
        int task = 100;
        var library = new Library(bookCount);

        CompletableFuture<Book>[] get = new CompletableFuture[task];
        CompletableFuture<Boolean>[] ret = new CompletableFuture[task];
        Book[] books = new Book[task];

        while (iterations-- > 0) {

            for (int i = 0; i < get.length; i++) {
                int number = rnd.nextInt(bookCount);
                get[i] = CompletableFuture.supplyAsync(() -> getBookByNumber(library, number, atomicInteger));
                books[i] = get[i].get();
            }

            for (int i = 0; i < get.length; i++) {
                int number = rnd.nextInt(bookCount);
                Book book = books[i];
                ret[i] = CompletableFuture.supplyAsync(() -> returnBook(library, book, atomicInteger));
                if (!TestHardLevel) ret[i].get();
            }
        }

        System.out.println("\nSecond test passed. All is correct.");

        Assertions.assertEquals(library.busyBookCount(), atomicInteger.get());
        Assertions.assertEquals(library.booksTheftCounterSum(), atomicInteger.get());
    }

    public Book getBookByNumber(Library library, int number, AtomicInteger atomicInteger) {

        try {
            var book = library.tryTakeBook(number, true);

            if (book != null) {
                atomicInteger.getAndIncrement();
                return book;
            }

        } catch (Library.LibraryExceptions ex) {
            System.out.println("LIBRARY EXCEPTIONS WAS THROWN");
        }
        return null;
    }

    public Boolean returnBook(Library library, Book book, AtomicInteger atomicInteger) {

        if (library.tryReturnBook(book)) {
            atomicInteger.getAndDecrement();
            return true;
        }
        return false;
    }
}