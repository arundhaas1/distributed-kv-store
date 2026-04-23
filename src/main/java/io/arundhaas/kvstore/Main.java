package io.arundhaas.kvstore;                                                                                                                                                                                      
                                                                                                                       
  import java.io.IOException;                                                                                                                                                                                        
  import java.nio.file.Files;
  import java.nio.file.Path;                                                                                                                                                                                         
                                                                                                                       
  public class Main {                     

      public static void main(String[] args) throws IOException {                                                                                                                                                    
          String walPath = "data/wal.log";
          Files.createDirectories(Path.of("data"));                                                                                                                                                                  
                                                                                                                       
//          System.out.println("=== Session 1: write some data ===");
//          try (KvStore<String, String> store = new KvStore<>(walPath)) {
//              store.put("user:1", "Alice");                                                                                                                                                                          
//              store.put("user:2", "Bob");                                                                                                                                                                            
//              store.put("user:3", "Charlie");                                                                                                                                                                        
//              store.delete("user:2");                                                                                                                                                                                
//                                                                                                                       
//              System.out.println("user:1 = " + store.get("user:1"));                                                                                                                                                 
//              System.out.println("user:2 = " + store.get("user:2"));   // null (deleted)
//              System.out.println("user:3 = " + store.get("user:3"));                                                                                                                                                 
//          }                                                                                                                                                                                                          
                                          
//          System.out.println();                                                                                                                                                                                      
          System.out.println("=== Session 2: reopen — data should still be there ===");                                                                                                                              
          try (KvStore<String, String> store = new KvStore<>(walPath)) {
              System.out.println("user:1 = " + store.get("user:1"));                                                                                                                                                 
              System.out.println("user:2 = " + store.get("user:2"));   // still null                                   
              System.out.println("user:3 = " + store.get("user:3"));                                                                                                                                                 
          }                                   
                                                                                                                                                                                                                     
          System.out.println();                                                                                        
//          System.out.println("=== WAL contents on disk ===");                                                                                                                                                        
//          Files.readAllLines(Path.of(walPath)).forEach(System.out::println);                                           
      }                                                                                                                                                                                                              
  }       